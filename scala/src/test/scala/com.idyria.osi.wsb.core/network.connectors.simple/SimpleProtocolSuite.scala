package com.idyria.osi.wsb.core.network.connectors.simple


import org.scalatest._
import com.idyria.osi.wsb.core.network._

import java.nio._
import java.io._

class SimpleProtocolSuite extends FunSuite  with GivenWhenThen {


    // Prepare Message and result
    //---------------
    var sendMessage = """<Envelope><Body></Body></Envelope>"""
    var expectedResult=s"Content-Length: ${sendMessage.getBytes.length}\n$sendMessage"
    var byteChunk = 2

    var messages : Map[String,String] = List(
        """<Envelope><Body></Body></Envelope>""",
        """<Envelope><Body></Body></Envelope>"""
        ).map( m => (m -> s"""Content-Length: ${m.getBytes.length}\n$sendMessage""")).toMap

    test("Send Standalone") {

        Given("A Client Protocol Handler")
        //-------------

        var clientHandler = new SimpleProtocolHandler(new NetworkContext)


        When("Sending on the client handler")
        //------------------
        var sendResult = clientHandler.send(ByteBuffer.wrap(sendMessage.getBytes))

        // Convert to string:
        println("Result: "+new String(sendResult.array()))

        Then("Output must match protocol")
        expect(expectedResult)(new String(sendResult.array()))



    }



    test("Receive Standalone one message in chuncks") {

        var toSend = ByteBuffer.wrap(expectedResult.getBytes)

        Given("A Server Protocol Handler")
        //-------------
        var  serverHandler = new SimpleProtocolHandler(new NetworkContext)

        serverHandler.onWith("start") {
            b : ByteBuffer =>
           // println(s"--------- Starting to analyse buffer with: ${b.remaining} elements -----------")
        }

        serverHandler.onWith("contentLength.foundFirstLine") {
            line : String =>
            println(s"--------- FOUND CONTENT LENGTH LINE: ${line} -----------")
        }

        serverHandler.onWith("contentLength.buffering") {
            c : Byte =>
            //println(s"--------- Content length buffering: ${c.asInstanceOf[Char]} -----------")
        }

         When("Receiving in chumps on the server handler")
        //---------------
        var currentPos = 0
        var resultBytes =new ByteArrayOutputStream()

        var lastResult = false
        toSend.limit(toSend.capacity)
        while (toSend.remaining > 0) {

            // Available bytes
            var bytesToRead = byteChunk
            if (bytesToRead>toSend.remaining) {
                bytesToRead = toSend.remaining
            }
            var readBytes = new Array[Byte](bytesToRead)

            //println("Bytes to read: "+bytesToRead+" with remaining: "+toSend.remaining )

            // Read
            toSend.get(readBytes)
            lastResult = serverHandler.receive(ByteBuffer.wrap(readBytes))


        }

        Then("Last returned boolean must be true")
        //---------------
        expect(true)(lastResult)

        And("The Produced Data must be the message")
        //------------------------------
        expect(sendMessage)(new String(serverHandler.availableDatas.head.array()))

    }



    test("Receive Standalone one message in one pass") {

        var toSend = ByteBuffer.wrap(expectedResult.getBytes)

        Given("A Server Protocol Handler")
        //-------------
        var  serverHandler = new SimpleProtocolHandler(new NetworkContext)

        serverHandler.onWith("start") {
            b : ByteBuffer =>
           // println(s"--------- Starting to analyse buffer with: ${b.remaining} elements -----------")
        }

        serverHandler.onWith("contentLength.foundFirstLine") {
            line : String =>
            println(s"--------- FOUND CONTENT LENGTH LINE: ${line} -----------")
        }

        serverHandler.onWith("contentLength.buffering") {
            c : Byte =>
            //println(s"--------- Content length buffering: ${c.asInstanceOf[Char]} -----------")
        }

         When("Receiving in one pass on the server handler")
        //---------------
        var lastResult = serverHandler.receive(toSend)


        Then("Last returned boolean must be true")
        //---------------
        expect(true)(lastResult)
        expect(1)(serverHandler.availableDatas.size)

        And("The Produced Data must be the message")
        //------------------------------
        expect(sendMessage)(new String(serverHandler.availableDatas.head.array()))


    }

    test("Receive Standalone two messages in one pass") {

        Given("A Server Protocol Handler")
        //-------------
        var  serverHandler = new SimpleProtocolHandler(new NetworkContext)

    }

    messages.foreach {
       case (message , protocolMessage) =>

        info(s"Message as input source: "+message)

        test("Receive Standalone on list of message:") {

        }
    }


   /* test("Simple Protocol over TCP") {



        var sendMessage = """
            <Envelope>
                <Body></Body>
            </Envelope>

        """

        Given("A client Simple TCP Connector")
        //-----------------------------------------
        var client = new SimpleMessageTCPConnector
        client.direction = AbstractConnector.Direction.Client

        Given("A server Simple TCP Connector")
        //-----------------------------------------
        var server = new SimpleMessageTCPConnector
        server.cycleToStart

        Then("Start")
        //---------------------
        server.cycleToStart
        server.started.acquire
        client.cycleToStart

        When("Sending a simple XML message through client")
        //--------------------
        client.send(ByteBuffer.wrap(sendMessage.getBytes))

        Then("The message should come out of the handler")
        //--------------------------



        // Stop
        //----------------------
        client.cycleToStop
        server.cycleToStop

    }*/


}