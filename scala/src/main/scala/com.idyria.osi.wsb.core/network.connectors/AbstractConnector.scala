/**
 *
 */
package com.idyria.osi.wsb.core.network

import com.idyria.osi.wsb.core.Lifecycle
import com.idyria.osi.wsb.core.Logsource

import java.util.concurrent._

import java.nio._

import scala.language.implicitConversions

/**
    Base class for Connectors.
    Contains the base parameters for handling connectors.

    The run() method of Thread should implement the long running I/O handling

    As type parameter must be given the NetworkContext type for the connector implementation

 * @author rleys
 *
 */
abstract class AbstractConnector[NT <: NetworkContext] extends Thread with Lifecycle with Logsource {

  /**
    Network This Connector is registered under
  */
  var network : Network = null

  /**
    CLIENT/SERVER Direction
  */
  var direction = AbstractConnector.Direction.Server

  /**
   * Set to true on stop,  Thread implementation must stop when this is set to true
   */
  var stopThread = false


  /**
    Semaphore used by implementatino to signal it is ready to operate
  */
  var started = new Semaphore(0);


  // User Interface
  //----------------------

  /**
    Used by clients to send datas
  */
  def send(data: ByteBuffer)

  /**
    Used to send to a specific network context peer
    Typically used by a server side that can handle multiple clients
  */
  def send(data:ByteBuffer, context: NT) 

  // Lifecycle
  //-----------------

  /**
   * Start a connector by starting a thread
   */
  def lStart = {

    this.start()


  }

  // Run
  //----------------

  /**
   * To be implemented by Server connector
   */
  override def run


}

/**

  This Companion object contains some utility conversions

*/
object AbstractConnector {

   object Direction extends Enumeration {
      type Direction = Value
      val Client , Server = Value
   }


   implicit def convertStringToByteBuffer(str : String ) : java.nio.ByteBuffer = java.nio.ByteBuffer.wrap(str.getBytes)

}