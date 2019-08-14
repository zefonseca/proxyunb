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
* This software is released under the MIT license. See the accompanying COPYING or LICENSE file.
*
*
* */


 case class Configuracao(host: String, porta: Int, numeroAtoresTrabalhadores: Int, maximoAtoresTrabalhadores: Int, arquivoWhitelist: Option[String] = None, arquivoBlacklist: Option[String] = None)
