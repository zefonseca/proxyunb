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
* Funções de estatísticas armazenadas em banco de dados via JDBC.
*
*
* This software is released under the MIT license. See the accompanying COPYING or LICENSE file.
*
* */


import java.sql._;
import scala.collection.mutable.ArrayBuffer

object DB {

  val driverJdbc = "org.h2.Driver"
  val urlJdbc    = "jdbc:h2:mem:rcproxy;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=2500;AUTOCOMMIT=ON;DATABASE_TO_UPPER=false;TRACE_LEVEL_FILE=2;"
  val usuarioJdbc = "sa"
  val senhaJdbc  = ""

  def criaTabelaMemoria : Unit = {
    val conDB = Proxy.conDB
    val sql = """CREATE MEMORY TABLE acessos(id IDENTITY, ip varchar(255), ms int, url varchar(255), autorizado TINYINT) NOT PERSISTENT;"""
    val stmt = conDB.createStatement()
    stmt.executeUpdate(sql)
    stmt.close()
  }

  def conecta: Connection = {
    Class.forName(driverJdbc)
    DriverManager.getConnection(urlJdbc, usuarioJdbc, senhaJdbc)
  }

  /*
  *
  * Armazena um acesso ao proxy.
  * O parametro "autorizado" deve ser 0 para negativo, 1 para positivo.
  *
  * */
  def armazenaAcesso(url: String, ip: String, miliSegundos: Long, autorizado: Int): Unit = {

    val conDB = Proxy.conDB
    val sql = s"""INSERT INTO acessos(id,ip,ms,url,autorizado) VALUES(NULL,?,?,?,?)"""
    val stmt = conDB.prepareStatement(sql)
    stmt.setString(1,ip)
    stmt.setLong(2,miliSegundos)
    stmt.setString(3,url)
    stmt.setInt(4,autorizado)
    stmt.execute()
    stmt.close()
  }

  def relatorioUrls: List[(String, Int, Double, Long)] = {
    val conDB = Proxy.conDB
    val sql1 = """SELECT count(*) FROM acessos"""
    val stmt1 = conDB.createStatement()
    val q = stmt1.executeQuery(sql1)
    q.next()
    val totalACESSOS = q.getInt(1)

    val sql = """SELECT url, AVG(ms), count(*) AS quantos FROM acessos GROUP BY url ORDER BY quantos DESC"""

    val stmt = conDB.createStatement()
    stmt.execute(sql)

    val rs = stmt.getResultSet

    val arrBuf = new ArrayBuffer[(String, Int, Double, Long)]()

    while (rs.next()) {
      
      val url = rs.getString(1)
      val ms  = rs.getLong(2)
      val quantos = rs.getInt(3)
      val freq = quantos.toDouble / totalACESSOS
      val tup = (url, quantos, freq, ms)
      arrBuf += tup
    }

    arrBuf.toList
  }

  def relatorioIps: List[(String, Int)] = {

    val conDB = Proxy.conDB
    val sql = """SELECT ip, count(*) AS QUANTOS FROM acessos GROUP BY ip ORDER BY QUANTOS DESC"""

    val stmt = conDB.createStatement()
    stmt.execute(sql)

    val rs = stmt.getResultSet

    val arrBuf = new ArrayBuffer[(String, Int)]()

    while (rs.next()) {
      val ip = rs.getString(1)
      val quantos = rs.getInt(2)
      val tup = (ip , quantos)
      arrBuf += tup
    }

    arrBuf.toList
  }

  def relatorioUrlsBloqueados: List[(String, String)] = {

    val conDB = Proxy.conDB
    val sql = """SELECT url, ip FROM acessos WHERE autorizado = 0"""

    val stmt = conDB.createStatement()
    stmt.execute(sql)

    val rs = stmt.getResultSet

    val arrBuf = new ArrayBuffer[(String, String)]()

    while (rs.next()) {
      val url = rs.getString(1)
      val ip  = rs.getString(2)

      val tup = (url, ip)
      arrBuf += tup
    }

    arrBuf.toList
  }


}
