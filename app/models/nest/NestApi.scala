package models.nest

import javax.inject.Inject

import com.firebase.client.{DataSnapshot, Firebase}
import vkorenev.firebase.ScalaFirebase

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class NestApi @Inject() (implicit exec: ExecutionContext) {
  def withNest[T](accessToken: String)(block: Firebase => Future[T]): Future[T] = {
    val rootRef = new Firebase(NestConfig.apiUrl)
    for {
      authData <- rootRef.authWithCustomToken(accessToken)
      result <- block(rootRef)
    } yield result
  }

  def getStructures(rootRef: Firebase): Future[Iterable[Structure]] = {
    val structuresRef = rootRef.child("structures")
    structuresRef.getSingleValue.map(structuresFromData)
  }

  private[this] def structuresFromData(data: DataSnapshot): Iterable[Structure] = {
    data.getChildren.asScala.map(structureFromData)
  }

  private[this] def structureFromData(data: DataSnapshot): Structure = {
    val id = data.child("structure_id").getValue.asInstanceOf[String]
    val name = data.child("name").getValue.asInstanceOf[String]
    Structure(id, name)
  }
}