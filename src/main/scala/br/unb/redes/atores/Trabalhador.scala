package br.unb.redes.atores

/*
*
* Universidade de Brasília
* CIC - Instituto de Ciência da Computação
* Redes de Computadores - 116572
* Prof: Marcos Fagundes Caetano
*
* Aluno: José M. A. Fonseca 12/0033933
*
* RCProxy - Proxy HTTP com filtragem e white/blacklists.
* Trabalho final do curso de Redes de Computadores.
*
* */


import akka.actor._
import java.net._
import java.io._
import java.lang.StringBuffer
import scala.collection.JavaConversions._
import java.io.StringWriter
import br.unb.redes.{DB, Configuracao}
import scala.io.Source

class Trabalhador(config: Configuracao) extends Actor {

  val urlRx = "(GET|POST)\\s+([^\\s]+)\\s+HTTP/([\\d]+)\\.([\\d]+).*".r
  val clRx  = "Content\\-Length\\:\\s+(\\d+)[^\\d]*".r
  val lineEnding = "\n"

   def receive = {

     case socket: Socket => {

       val isr = new InputStreamReader(socket.getInputStream())
       val bfr = new BufferedReader(isr)

       val enderecoLocal = socket.getLocalAddress
       val ipLocal = enderecoLocal.getHostAddress
       val portaLocal = socket.getLocalPort

       val enderecoRemoto = socket.getInetAddress
       val ipRemoto = enderecoRemoto.getHostAddress
       val portaRemota = socket.getPort

       var requestMethod = ""
       var url = ""
       var contentLength = 0
       var autorizado = true

         var lin = ""
         var meuBuffer = new StringBuffer()
         var postBuffer = new StringBuffer()

         do {
           lin = bfr.readLine()

           lin match {
             case urlRx(metodo, urlLocal, versaoA, versaoB) => {
               url = urlLocal
               requestMethod = metodo
               autorizado = siteAutorizado(urlLocal)
             }
             case _ => /* Ignorar */
           }

           if (lin != null) {
             meuBuffer.append(lin.trim + "\n")
           }

         } while ((lin != null) && (lin != "\r\n") && (lin != ""))

         if(requestMethod.toUpperCase == "POST"){
           /* Preenche o buffer dos dados POST */
           for (x <- 0 to contentLength) {
             postBuffer.append(bfr.read().asInstanceOf[Char])
           }
         }

         /* Mensagem completa recebida, processa. */
         if (autorizado){
           val t1 = System.currentTimeMillis()
           processaRequisicao(socket, meuBuffer.toString, postBuffer.toString)
           val t2 = System.currentTimeMillis()
           val delta = t2 - t1
           println("\n\nREQUISICAO")
           println("IP DE ORIGEM: " + ipRemoto)
           println("PORTA DE ORIGEM: " + portaRemota)
           println("METODO HTTP " + requestMethod)
           println("URL REQUISITADO " + url)
           println("IP DESTINO " + ipLocal)
           println("PORTA DESTINO " + portaRemota)


             println("ACESSO PERMITIDO POR WHITE/BLACKLIST")
             DB.armazenaAcesso(url, ipRemoto, delta, 1)

           println("TEMPO DECORRIDO " + delta + " milisegundos")

         }else{
           println("URL BLOQUEADO POR BLACKLIST")
           DB.armazenaAcesso(url, ipRemoto, 0, 0)
           devolveErro(socket, 403, "Página bloqueada pelo administrador de rede. Favor entrar em contato com a administração.")
         }

       socket.close()
       bfr.close()
       isr.close()

     }
   } /* Termina receive */


  def processaRequisicao(socket: Socket, bufferHeader: String, postData: String = "") : Unit = {

    val linhas = bufferHeader.split("\n")

    var metodo: String = ""
    var url: URL      = null
    var versaoMaior: Int = 0
    var versaoMenor: Int = 0
    var userAgent = ""
    var conClose: Boolean = false

    /* Caso ja tenha comecado a transferencia, nao envia headers em caso de erro. */
    var comecouTransferencia = false
    var headersAdicionais: StringBuffer = new StringBuffer()

    linhas.zipWithIndex.foreach{
      case(l, i) => {

        if (i == 0) {
          /* Linha zero = metodo, url e protocolo. */
          l match {
            case urlRx(metodoA, urlA, versaoA, versaoB) => {
              metodo = metodoA
              url = new URL(urlA)
              versaoMaior = versaoA.toInt
              versaoMenor = versaoB.toInt
            }
          }
        }else {
          /* Demais linhas = cabeçalhos. */
          headersAdicionais.append(l)

          /* Procura o header User-Agent e atribui corretamente. */
          val ckv = l.split("\\:")
          if(ckv(0).matches("User\\-Agent.*")){
            userAgent = ckv(1).trim
            System.setProperty("http.agent", userAgent)
          }
          if(ckv(0).matches("Connection.*?close.*")){
            conClose = true
          }
        }
      }
    }

    try{

      val conn     = url.openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestProperty("User-Agent", userAgent)
      conn.setInstanceFollowRedirects(true)
      conn.setDefaultUseCaches(false)

      if (conn.getResponseCode >= 400) {
        devolveErro(socket, conn.getResponseCode, conn.getResponseMessage)
        socket.close()
        return
      }

      val os     = socket.getOutputStream
      val is     = conn.getInputStream
      val fields = conn.getHeaderFields

      /* Retorna primeiro o codigo HTTP.
      *  OBS. Headers Podem vir do hash fora da ordem
      *   recebida da rede. */
      for(f <-fields) {

        f match {
          case (a,b) => {

            /* Cod HTTP nao tem chave. */
            if (a == null) {
              val resp: String = if (b.head.endsWith("\r")) {  b.head.trim + lineEnding }
                         else if (b.head.endsWith(lineEnding)) { b.head }
                         else if (b.head.endsWith("\n")) {  b.head.trim + lineEnding }
                         else {  b.head  + lineEnding }

              comecouTransferencia = true
              resp.replaceAll("HTTP/1.1", "HTTP/1.0").foreach{c=>
                os.write(c)
              }
              //System.out.write(resp.getBytes())
            }

          }
        }
      }

      for(f <-fields) {
        f match {
          case (a,b) => {
            /* Demais headers */
            if ( (a != null) && (!b.isEmpty) ) {

              if (a.matches("Connection.*")){
                val resp: String = "Connection: close"  + lineEnding

                comecouTransferencia = true
                resp.foreach{c=>
                  os.write(c)
                }

              } else if( a.matches("Accept\\-Encoding\\:.*")  || a.matches("Range\\:.*") ) {
                /* Ignorar. */
              } else {
                val resp: String = a + ": " + (if (b.head.endsWith("\r")) b.head.trim + lineEnding
                else if (b.head.endsWith(lineEnding)) b.head
                else if (b.head.endsWith("\n")) b.head.trim + lineEnding
                else b.head  + lineEnding)

                comecouTransferencia = true
                resp.foreach{c=>
                  os.write(c)
                }

              }


              //System.out.write(resp.getBytes())
            }

          }
        }
      }

      /* Encerra cabecalhos. */
      lineEnding.foreach{c=>
        os.write(c)
      }

      var dadoLido = is.read()

      var nx = 0
      while ( dadoLido != -1  )  {
        try {
          os.write(dadoLido)
          nx += 1
        }catch{
          case e: Throwable =>   dadoLido = -1
        }

        try {
          dadoLido = is.read()
        }catch{
          case e: Throwable => dadoLido = -1
        }
      }

      try {
        os.flush()
      }catch {
        case e: Throwable => println("Exceção ao descarregar OutputStream");
      }

      os.close()
      is.close()

      if (conClose) {
        socket.close()
      }


    }catch {
      case f404: java.io.FileNotFoundException  => {
        if (!comecouTransferencia) {

          devolveErro(socket, 400, "RCProxy - Página não encontrada.")
        }else {
          return
        }

      }
      case s500: java.net.SocketException => {
        if (!comecouTransferencia) {
          devolveErro(socket, 500, "RCProxy - Erro Interno do Proxy.")
        }else {
          return
        }


      }
      case e: Throwable => e.printStackTrace()
    }



  }

  def devolveErro(socket: Socket, codigo: Int, mensagem: String): Unit = {
    val osWriter = new OutputStreamWriter(socket.getOutputStream)
    val saidaBW  = new BufferedWriter(osWriter)

    val erro = if (codigo == 500) "Internal Server Error" else if (codigo == 404) "Not Found" else if (codigo == 403) "Forbidden" else "Not Found"

    saidaBW.write(s"""HTTP/1.0 $codigo $erro\r\nVary: accept-encoding\r\nServer: UnB-CIC-RCProxy\r\nConnection: close\r\n\r\nRCProxy - Erro Interno HTTP $codigo : $mensagem""")

    saidaBW.flush()
  }


  def siteAutorizado(url: String): Boolean = {

    var autorizado:Boolean = true

    val whiteList = config.arquivoWhitelist
    val blackList = config.arquivoBlacklist

    blackList match {
      case Some(arquivo) => {
        val f = new File(arquivo)
        if (f.exists()) {
          Source.fromFile(arquivo).getLines().foreach{ l =>
            val regEx = l
            /* Match completa inicio e fim de linha, permitindo regexes parciais como em Perl. */
            if (url.matches(".*?" + regEx + ".*")) {
              autorizado = false
            }
          }
        }
      }
      case None => autorizado = true
    }

    whiteList match {
      case Some(arquivo) => {
        val f = new File(arquivo)
        if (f.exists()) {
          Source.fromFile(arquivo).getLines().foreach{ l =>
            val regEx = l
            if (url.matches(regEx)) {
              autorizado = true
            }
          }

        }
      }
      case None => autorizado = true
    }

    autorizado
  }


}
