package com.idyria.osi.wsb.core.network.connectors.tcp

import scala.collection.JavaConversions._

import java.util.concurrent._

import java.net._

import org.scalatest.BeforeAndAfterAll
import org.scalatest.GivenWhenThen
import org.scalatest.FeatureSpec
import org.scalatest.GivenWhenThen
import java.net.URLClassLoader
import java.net.URL

import java.io._
import java.nio._
import java.nio.charset._

import com.idyria.osi.wsb.core.network.AbstractConnector

class TCPConnectorSuite extends FeatureSpec with GivenWhenThen {


    feature("Server Direction") {



        scenario("Server Socket Channel Setup") {

            given("A started connector")
            //---------------------

            var started = new Semaphore(0);
            var connector = new TCPConnector() {
                port=9898

                on( "server.started") {

                    println("Wow, Connector is started")
                    started.release
                }

                // Dummy
                def protocolReceiveData( buffer : ByteBuffer, context: TCPNetworkContext) = {

                }

                // Dummy
                def protocolSendData(buffer : ByteBuffer,context: TCPNetworkContext) = {

                }



            }
            connector.cycleToInit

            //-- Start It
            connector.cycleToStart

            assert(started.tryAcquire(1,TimeUnit.SECONDS) == true)

            then("it can be stoped and cleaned")
            //----------------------

            //-- Stop It
            connector.cycleToStop

            // Check
            //--------------

            // Thread must die
            try {
                connector.join(1000)
            } catch {
                case e : Throwable => {
                    assert(false)
                }
            }

        }

        scenario("Server Socket Accept connection") {


            given("A Started TCP Connector")
            //-----------------
            var contextIdentifier = ""
            var connected = new Semaphore(0);
            var connector = new TCPConnector() {
                port=9898

                on ("server.accepted") {

                    println("Wow, in Connection Handler")
                    connected.release
                }

                 onWith("server.accepted") {
                    ctx : TCPNetworkContext =>
                    println("Connection context identifier: "+ctx.toString)
                    contextIdentifier = ctx.toString
                }

                cycleToInit
                cycleToStart

                // Dummy
                def protocolReceiveData(buffer : ByteBuffer,context: TCPNetworkContext) = {

                }

                // Dummy
                def protocolSendData(buffer : ByteBuffer,context: TCPNetworkContext) = {

                }

            }




            then("Make a simple  Standard Socket connection")
            //------------------
            var sock = new Socket("localhost",9898)



            then("The Accepted connection handle should trigger the connected semaphore")
            //---------------

            var connectedResult = connected.tryAcquire(1,TimeUnit.SECONDS)

            //-- Stop It
            connector.cycleToStop
            sock.close
            connector.join(1000)


            expect(true)(connectedResult)






        }

        scenario("Server Socket Read a data line") {

            given("A Started TCP Connector")
            //-----------------
            var received = new Semaphore(0);
            var connector = new TCPConnector() {
                port=9898

                on ("server.read") {

                    println("Wow, Server got read data")

                }

                cycleToInit
                cycleToStart

                // Receive until end of line
                var receivedLine = ""
                def protocolReceiveData(buffer : ByteBuffer,context: TCPNetworkContext) = {

                    // on End of line, activate semaphore
                    var buff = Charset.forName("UTF-8").decode(buffer)
                    if (buff.array.contains('\n')) {
                        received.release
                        receivedLine = buff.toString
                    }


                }

                // Dummy
                def protocolSendData(buffer : ByteBuffer,context: TCPNetworkContext) = {

                }

            }

            then("Make a simple Standard Socket connection, and send a line of data")
            //------------------
            var socket = new Socket("localhost",9898)

            var printStream = new PrintStream(socket.getOutputStream)

            printStream.println("Ping")

            // Wait for results
            var receivedResult = received.tryAcquire(1,TimeUnit.SECONDS)


            // Close
            //-------------
            connector.cycleToStop
            socket.close
            connector.join(1000)

            // Check
            expect(true)(receivedResult)
            expect("Ping\n")(connector.receivedLine)

        }


    }



    feature("Client / Server Direction") {

        abstract class DummyTestConnector extends TCPConnector {



        }

        scenario("Client / Exchanges a line with server") {

            given("A Started Server TCP Connector")
            //-----------------------
            var serverStarted = new Semaphore(0);
            var serverConnector = new DummyTestConnector() {

                port=9898


                on("server.started") {
                    serverStarted.release
                }

                on("server.accepted") {
                    println("Connection seen on server")
                }

                // Receive And echo back
                def protocolReceiveData(buffer : ByteBuffer,context: TCPNetworkContext) = {

                    // Get Received line
                    var line = Charset.forName("UTF-8").decode(buffer).toString

                    println("Server received something: "+line)

                    // Send back
                    protocolSendData(ByteBuffer.wrap(("Repeat: "+line).getBytes),context);


                }

                // Send a line
                def protocolSendData(buffer : ByteBuffer,context: TCPNetworkContext) = {

                    // Send
                    context.socket.write(buffer)

                    println("Server send something")


                }

                cycleToInit
                cycleToStart

            }
            assert(serverStarted.tryAcquire(1,TimeUnit.SECONDS)==true)

            given("A Started Client TCP Connector")
            //-----------------------
            var clientStarted = new Semaphore(0);
            var receivedResult = new Semaphore(0);
            var clientConnector = new DummyTestConnector() {

                port=9898
                direction = AbstractConnector.Direction.Client



                on("client.started") {

                    println("Client STARTED!")

                    clientStarted.release

                }


                // Send over a line
                var sendLine = ""
                def protocolSendData(buffer : ByteBuffer,context: TCPNetworkContext) = {

                    // Get line
                    //sendLine = Charset.forName("UTF-8").decode(buffer).toString

                    // Send
                    context.socket.write(buffer)
                    context.socket.write(ByteBuffer.wrap("\n".getBytes))

                    println("Client send some datas: "+sendLine)

                }

                // Trigger received line
                var receivedLine = ""
                def protocolReceiveData(buffer : ByteBuffer,context: TCPNetworkContext) = {

                    // Get Received line
                    receivedLine = Charset.forName("UTF-8").decode(buffer).toString

                    receivedResult.release


                }

                cycleToInit
                cycleToStart

            }
            assert(clientStarted.tryAcquire(2,TimeUnit.SECONDS)==true)


            then("Client Sends a string on a line, and should get it back")
            //------------------------------
            var sendBase = "Hello"
            clientConnector.protocolSendData(ByteBuffer.wrap(sendBase.getBytes),clientConnector.clientNetworkContext)

            // Wait For receive
            var received = receivedResult.tryAcquire(2,TimeUnit.SECONDS)


            // Close
            //--------------
            serverConnector.cycleToStop
            clientConnector.cycleToStop

            // Checks
            //--------------
            expect(true)(received)
            expect("Repeat: "+sendBase+"\n")(clientConnector.receivedLine)

        }


    }


}


