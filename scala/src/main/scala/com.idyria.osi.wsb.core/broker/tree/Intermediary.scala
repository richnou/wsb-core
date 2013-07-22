/**
 *
 */
package com.idyria.osi.wsb.core.broker.tree

import com.idyria.osi.wsb.core.message.Message

import scala.util.matching.Regex
import com.idyria.osi.ooxoo.core.buffers.structural.XList
import com.idyria.osi.ooxoo.core.buffers.structural.ElementBuffer
import com.idyria.osi.ooxoo.core.buffers.structural.xattribute
import com.idyria.osi.ooxoo.core.buffers.structural.xelement

/**
 * @author rleys
 *
 */
class Intermediary extends ElementBuffer {

  /**
   * Filter is used by Broker to determine if a message should go through this intermediary.
   */
  @xattribute
  var filter : Regex = null

  @xelement
  var intermediaries : XList[Intermediary] = XList[Intermediary] {  new Intermediary }

  // Up/ Down closures for user processing
  //---------------
  var downClosure : ( Message => Unit) = null

  var upClosure : ( Message => Unit) = null


  // Up/Down runtime
  //---------------
  final def down( message: Message) = {

  }

  final def up(message: Message) = {

  }

  def intermediaryTest = println("Hi!")

}

object Intermediary {



}
