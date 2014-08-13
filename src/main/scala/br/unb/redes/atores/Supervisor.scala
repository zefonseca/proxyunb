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
import br.unb.redes.Configuracao
import java.net.ServerSocket

class Supervisor extends Actor {

   var numTrabalhadores: Int = 0
   var listener: ServerSocket = null
   var running = true

   def receive = {


     /*
      *  Quando o supervisor recebe uma config, começa a ouvir conexões
      *  usando essa config.
      *
      * */
     case c: Configuracao => {

       /* Recebeu configuração, passa a ouvir conexões. */
       println("INICIANDO SERVIDOR " + c)
       listener = new ServerSocket(c.porta)

       do {
           val props = Props(classOf[Trabalhador], c)
           numTrabalhadores += 1
           val actor = context.actorOf(props)

           val clientSocket = listener.accept()
           actor ! clientSocket
       } while(running)


     }

     case "Termina" => {
       numTrabalhadores -= 1
       running = false
       listener.close()
     }

   }

}
