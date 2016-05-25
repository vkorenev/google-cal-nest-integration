package models.nest

import com.firebase.client.{DataSnapshot, Firebase, ValueEventListener}
import org.junit.runner.RunWith
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class NestApiSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {
  "NestApi" should {
    "get structures" in {
      val expectedStructure = Structure("id", "name")
      val nestApi = new NestApi()
      val rootRef = mock[Firebase]
      val structuresRef = mock[Firebase]
      rootRef.child("structures") returns structuresRef
      structuresRef.addListenerForSingleValueEvent(any[ValueEventListener]) responds {
        case listener: ValueEventListener =>
          val structureData = mock[DataSnapshot]
          structureData.child("structure_id") returns (mock[DataSnapshot].getValue() returns expectedStructure.id)
          structureData.child("name") returns (mock[DataSnapshot].getValue() returns expectedStructure.name)

          listener.onDataChange(mock[DataSnapshot].getChildren returns Iterable(structureData).asJava)
      }

      val structuresFuture = nestApi.getStructures(rootRef)
      structuresFuture must contain(exactly(expectedStructure)).await
    }
  }
}
