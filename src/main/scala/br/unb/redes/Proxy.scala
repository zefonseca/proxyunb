package br.unb.redes

/*
*
* Universidade de Brasília
* CIC - Instituto de Ciência da Computação
* Redes de Computadores - 116572
* Prof: Marcos Fagundes Caetano
*
* Aluno: Jose Fonseca (https://zefonseca.com/)
*
* RCProxy - Proxy HTTP com filtragem e white/blacklists.
* Trabalho final do curso de Redes de Computadores.
*
*
* This software is released under the MIT license. See the accompanying COPYING or LICENSE file.
*
* */

import akka.actor._
import java.util.Properties
import java.io.File
import java.io.FileInputStream
import java.sql.Connection
import java.io._
import com.typesafe.config.ConfigFactory
import java.awt.Desktop
import java.net.URI

object Proxy {

  val ARQUIVO_CONFIG = "/etc/unbproxy.properties"
  val NOME_SISTEMA_ATORES = "Principal"


  /* Threads daemonic permitem terminar a app com CTRL+D */
  val customConf = ConfigFactory.parseString("""

akka {
  daemonic = on
}

                                            """)

  /* Conecta ao BD e cria tabela na memória volátil. */
  val conDB: Connection = DB.conecta
  DB.criaTabelaMemoria

  val console = System.console()

 /*
  * main: Ponto de entrada do programa.
  *
  *
  * - Lê configurações de /etc/unbproxy.properties
  *
  *
  * Modelo de multithreading: Atores Akka
  *
  * - main Inicializa o sistema de atores Akka que será composto por:
  *   - Ator supervisor: Monitora conexões HTTP e despacha para um ator trabalhador.
   *  - Atores trabalhadores: recebem parâmetros da conexão HTTP, e realizam filtragem e devolvem conteúdo, se tiver.
  *
  *
  * */
  def main(args: Array[String]): Unit = {

    val sistemaAtores = ActorSystem(NOME_SISTEMA_ATORES, ConfigFactory.load(customConf))

    /* Tenta carregar configuracoes do ARQUIVO_CONFIG.
    *  Caso falhe, sai imediatamente.
    * */
    val propriedades: Properties = carregaProps(ARQUIVO_CONFIG, sistemaAtores) match {
      case Some(p) => p
      case None => saiComErro("Impossível abrir arquivo " + ARQUIVO_CONFIG + ". Verifique as permissões e tente novamente.", sistemaAtores); null
    }

    /* Recebendo properties, carregaConfiguracao nunca falha pois tem valores padrão. */
    val configuracao = carregaConfiguracao(propriedades)

    /* Cria o ator supervisor. */
    val atorSupervisor = sistemaAtores.actorOf(Props[atores.Supervisor], "supervisor")

    /* Passa a configuração para o supervisor, que passa a ouvir conexões. */
    atorSupervisor ! configuracao

    val leitor = console.reader
    var lido: Int = 0

    do  {
      lido = leitor.read()
    } while ((lido != 4) && (lido != -1))

    /* CTRL+D foi pressionado. */
    println("Encerrando threads...")
    sistemaAtores.shutdown()
    mostraEstatisticas
    println("RCPROXY TERMINADO")
    Thread.sleep(8000)
    System.exit(0)
  }



  /* ---------------------------------------------------------------------------------------------------------------- */



  def saiComErro(mensagem: String, sistema: ActorSystem): Unit = {
    println("Erro Fatal: " + mensagem + "\nImpossível continuar. Corrija os erros indicados e tente novamente.")
    sistema.shutdown()
    System.exit(1)
  }



  /* ---------------------------------------------------------------------------------------------------------------- */


  def mostraEstatisticas: Unit = {

    val arquivoRelatorio = "/tmp/relatorio.html"

    val urls = DB.relatorioUrls
    val bloq = DB.relatorioUrlsBloqueados
    val ips  = DB.relatorioIps

    val html =
      """<html><head>
           <meta charset="UTF-8">
           <title>Relatório de Acessos RCProxy - Redes de Computador</title>
           <script src="http://netdna.bootstrapcdn.com/bootstrap/3.1.1/js/bootstrap.min.js"></script>
           <link rel="stylesheet" type="text/css" href="http://netdna.bootstrapcdn.com/bootstrap/3.1.1/css/bootstrap.min.css">
        </head><body><div class="container">
        <h1>URLs Mais Acessadas</h1>
        <table class="table table-striped">
          <thead>
          <tr>
        |<th>#</th>
            <th>URL</th>
            <th>Nr. Acessos</th>
        <th>Porcentagem</th>
        <th>Tempo Médio(ms)</th>
        </tr></thead><tbody>""" + urls.zipWithIndex.map{ case(x,i) =>

        s"""<tr>
                <td>$i</td>
                <td>${x._1}</td>
                <td>${x._2}</td>
                <td>${x._3}</td>
                <td>${x._4}</td>
            </tr>"""
      }.mkString("\n") + """</tbody></table>

      <div class="clearfix" style="margin-top: 20px;">&nbsp;</div>
      <h1>Log de IPs</h1>
       <table class="table table-striped">
                 <thead>
                 <tr>
                                                                   <th>#</th>
                   <th>IP</th>
                   <th>Nr. Acessos</th>
              </tr></thead><tbody>
                         """+ips.zipWithIndex.map{ case (x,i) =>

        s"""<tr>                <td>$i</td>
                <td>${x._1}</td>
                <td>${x._2}</td>
            </tr>"""
      }.mkString("\n")+"""
       </tbody></table>

      <div class="clearfix" style="margin-top: 20px;">&nbsp;</div>

      <h1>URLs Bloqueados</h1>


      <table class="table table-striped">
                <thead>
                <tr>
                  <th>#</th>
                  <th>URL Bloqueado</th>
                  <th>IP</th>
             </tr></thead><tbody>
                       """ +bloq.zipWithIndex.map{ case(x,i) =>

        s"""<tr>                <td>$i</td>
                <td>${x._1}</td>
                <td>${x._2}</td>
            </tr>"""
      }.mkString("\n")+ """
      </tbody></table>

                         """ + """ </div></body></html>"""

    val f   = new File(arquivoRelatorio)
    val fos = new FileOutputStream(f)
    val osw = new OutputStreamWriter(fos)
    val pw  = new PrintWriter(osw)

    pw.print(html)
    pw.close()
    osw.close()
    fos.close()

    val meuDesktop = Desktop.getDesktop()
    val uri = new URI("file://" + arquivoRelatorio)

    meuDesktop.browse(uri)


  }


  /* ---------------------------------------------------------------------------------------------------------------- */


  def carregaProps(caminho: String, sistemaAtores: ActorSystem) : Option[Properties] = {

    val arqConfig     = new File(caminho)

    if (!arqConfig.exists()) {
      saiComErro("Arquivo " + ARQUIVO_CONFIG + " não encontrado.", sistemaAtores)
    }

    val props = new Properties()

    val fis = try{
      new FileInputStream(arqConfig)
    } catch {
      case e: Throwable => null
    }

    if (fis == null) {
      None
    } else {
      props.load(fis)
      Some(props)
    }

  }



  /* ---------------------------------------------------------------------------------------------------------------- */




  def carregaConfiguracao(p: Properties) : Configuracao = {

      val host  = p.getProperty("host", "localhost")
      val port  = p.getProperty("porta", "8080")
      val atoresTrabalhadores = p.getProperty("atores_trabalhadores", "5")
      val maximoAtoresTrabalhadores = p.getProperty("maximo_atores_trabalhadores", "10")
      val arquivoWhitelist = p.getProperty("arquivo_whitelist", "/etc/unbproxy.whitelist")
      val arquivoBlacklist = p.getProperty("arquivo_blacklist", "/etc/unbproxy.blacklist")

      val portNumber = port.toInt
      val atores = atoresTrabalhadores.toInt
      val maxAt = maximoAtoresTrabalhadores.toInt
      Configuracao(host, portNumber, atores, maxAt, Some(arquivoWhitelist), Some(arquivoBlacklist))
  }


}
