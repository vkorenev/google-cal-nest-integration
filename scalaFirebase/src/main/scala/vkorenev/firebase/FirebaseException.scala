package vkorenev.firebase

import com.firebase.client.FirebaseError

case class FirebaseException(error: FirebaseError) extends Exception(error.getMessage)
