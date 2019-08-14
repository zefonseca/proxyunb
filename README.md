# RCProxy 

Proxy HTTP básico com filtragem e white/blacklists em linguagem Scala.

##### Trabalho final do curso de Redes de Computadores. 

    Universidade de Brasília
    CIC - Instituto de Ciência da Computação
    Redes de Computadores - 116572
    Prof: Marcos Fagundes Caetano
    Aluno: Jose Fonseca (https://zefonseca.com/)

### Instalação

É preciso instalar o SBT : http://www.scala-sbt.org/

No diretorio raiz, digite o comando:

    sbt run

As dependências Java do projeto serão baixadas automaticamente pelo SBT.

### Configuração

São necessários os arquivos:
* /etc/unbproxy.blacklist
* /etc/unbproxy.properties
* /etc/unbproxy.whitelist

Há arquivos-exemplo de white, blacklist e properties no diretorio doc desta distribuição.

### Licença

This software is released under the MIT license. See the accompanying COPYING or LICENSE file.

### Autor

Jose Fonseca (https://zefonseca.com/)