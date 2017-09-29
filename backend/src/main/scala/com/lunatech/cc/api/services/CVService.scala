package com.lunatech.cc.api.services

import java.util.UUID

import cats.implicits._
import com.lunatech.cc.api.GoogleUser
import com.lunatech.cc.models.CVData

import scala.reflect.runtime.universe.TypeTag
import doobie.imports.{Meta, _}
import fs2.Task
import io.circe.{Decoder, Encoder, Json}
import io.circe.parser.parse
import io.circe.syntax._
import org.postgresql.util.PGobject

trait CVService {


  def findByPerson(user: GoogleUser): List[Json]

  def findById(email: String): List[Json]

  def findAll: Map[String, List[Json]]

  def insert(email: String, cv: Json): Int

  def delete(cvid: UUID): Int

  def get(cvid: UUID): Option[Json]
}

class PostgresCVService(transactor: Transactor[Task]) extends CVService {

  implicit val JsonMeta: Meta[Json] =
    Meta.other[PGobject]("json").nxmap[Json](
      a => parse(a.getValue).leftMap[Json](e => throw e).merge, // failure raises an exception
      a => {
        println(a)
        val o = new PGobject
        o.setType("json")
        o.setValue(a.noSpaces)
        o
      }
    )


  override def findByPerson(user: GoogleUser): List[Json] = findById(user.email)

  override def findById(email: String): List[Json] =
    sql"SELECT cv FROM cvs WHERE person = ${email} ORDER BY created_on DESC".query[Json].list.transact(transactor).unsafeRun()

  override def findAll: Map[String, List[Json]] = {

    val folder: (List[CVData]) => Map[String, List[Json]] = (x: List[CVData]) => x.foldRight(Map[String, List[Json]]()) ((cvd: CVData, aggr: Map[String, List[Json]]) => {
      val l =   aggr.get(cvd.email).map( l => cvd.cv :: l).getOrElse(List(cvd.cv))
      aggr + (cvd.email -> l)
    } )
    val out = sql"SELECT person, cv FROM cvs ORDER BY person, created_on".query[CVData].process.list.transact(transactor).unsafeRun()
    folder(out)
  }


  override def insert(email: String, cv: Json): Int =
    sql"INSERT INTO cvs (id, person, cv, created_on) VALUES (${UUID.randomUUID.toString} :: UUID, $email, $cv, CURRENT_TIMESTAMP)".update.run.transact(transactor).unsafeRun()

  override def delete(cvid: UUID): Int = {
    val query = sql"DELETE FROM cvs WHERE id=${cvid.toString} :: UUID"
    query.update.run.transact(transactor).unsafeRun()
  }

  override def get(cvid: UUID): Option[Json] = sql"SELECT cv FROM cvs WHERE id=${cvid.toString} :: UUID".query[Json].option.transact(transactor).unsafeRun()

}
