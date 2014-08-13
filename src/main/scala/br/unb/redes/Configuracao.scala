package br.unb.redes

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


 case class Configuracao(host: String, porta: Int, numeroAtoresTrabalhadores: Int, maximoAtoresTrabalhadores: Int, arquivoWhitelist: Option[String] = None, arquivoBlacklist: Option[String] = None)
