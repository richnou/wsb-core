package com.idyria.osi.wsb.core.network.connectors.tcp

import scala.collection.JavaConversions._

import com.idyria.osi.wsb.core.message.HTTPMessage

import com.idyria.osi.wsb.core.network.AbstractConnector
import com.idyria.osi.wsb.core.network.protocols.ProtocolHandler

import com.idyria.osi.wsb.core.broker.MessageBroker
import com.idyria.osi.wsb.core.network._

import com.idyria.osi.tea.listeners.ListeningSupport

import scala.io.Source
import javax.net.ServerSocketFactory
import java.net.InetSocketAddress
import java.io.PrintStream
import java.nio.channels._
import java.nio._

/**

    TCP Connector is a base connector for TCP connections.
    It provides a high level data send/receive interface for the user, and manages connections with the Connector interface



*/
abstract class TCPConnector extends AbstractConnector with ListeningSupport {




    /**
     * Connection address
     */
    var address = "localhost"

    /**
        Connection Port
    */
    var port = 8083



    // Server Runtime Fields
    //-----------

    /**
        Server Socket used for server connection

        @group server
    */
    var serverSocket : ServerSocketChannel = null

    /**
        Server Socket Selector

        @group server
    */
    var serverSocketSelector : Selector = null

    /**

        Maps a string to te client handler, for backpath matching

        @group server
    */
    var clientsContextsMap =  Map[String,TCPNetworkContext]()


   //var onAcceptConnection   = {  }

    // Client Runtime Fields
    //-----------------

    var clientSocket : SocketChannel = null

    var clientNetworkContext : TCPNetworkContext = null



    // Protocol Implementation
    //----------------

    /**
        Send through protocol

        @throw RuntimeExeception if no client context is available
    */
    def send(buffer : ByteBuffer) = {

        require(this.clientNetworkContext!=null)

        protocolSendData(buffer,this.clientNetworkContext)


    }
    def protocolReceiveData(buffer : ByteBuffer,context: TCPNetworkContext)

    def protocolSendData(buffer : ByteBuffer,context: TCPNetworkContext)



    // LifeCycle
    //-------------------

    /**
        Prepare Socket

    */
    override def lInit = {

        logInfo ("Creating Socket")

        // Create Server Socket
        //----------------------------
        /*if (this.direction == AbstractConnector.Direction.Server) {
             this.serverSocket = ServerSocketChannel.open();
        }*/



    }

    /**
        Start Server Socket Thread
    */
    override def lStart = {

        this.start
/*
        // Start Server Thread
        //--------------
        if (this.direction == AbstractConnector.Direction.Server) {
            this.start
        }
        // Connect to server as client
        //---------------------
        else {

            logInfo(s"Starting TCP Connector as client on $address:$port")

            // Connect
            this.clientSocket = SocketChannel.open(new InetSocketAddress(this.address,this.port))

            // Record Network Context
            this.clientNetworkContext = new TCPNetworkContext(this.clientSocket)

            logInfo(s"Client Started")

            @->("client.started")

        }*/

    }


    override def lStop = {

        
        // Stop all threads
        this.stopThread = true

        // Stop Server socket
        //------------
        if (this.direction == AbstractConnector.Direction.Server) {

            // Close Selector to stop operations on thread
            this.serverSocketSelector.close

        } else {

            this.stopThread = true
            this.clientSocket.close
        
        }

    }


    // Connector Run Method
    //------------------------

    //-- React on common started to signal ready to go
    on("common.started") {

        started.release(Integer.MAX_VALUE)
    }

    /**
        Start this connector in Listening mode if in SERVER direction,
        or tries to connect to  target address if in CLIENT direction

    */
    override def run = {

        // Common
        //---------------

        //-- Prepare Read Buffer
        //----------------
        var readBuffer = ByteBuffer.allocate(4096) // (Buffer of a page size per default)

        // Server Mode
        //-------------------
        if (this.direction == AbstractConnector.Direction.Server) {

            @->("server.start")

            logInfo(s"Starting TCP Connector on $port")

            // Bind
            //--------------
            this.serverSocket = ServerSocketChannel.open();
            this.serverSocket.bind(new InetSocketAddress(address,port))

            // Register Selector for all operations
            // !! Selector only works on non blocking sockets
            //----------------------
            this.serverSocketSelector = Selector.open()
            this.serverSocket.configureBlocking(false);
            this.serverSocket.register(this.serverSocketSelector,SelectionKey.OP_ACCEPT)


            // Loop on Selection and handle actions
            //------------------
            @->("server.started")
            @->("common.started")
            while(!this.stopThread) {

                try {

                    // Select blocking, will throw an exception if socket is closed
                    var selected = this.serverSocketSelector.select


                //println("Updated selected keys updated with: "+selected+" keys")
                //if (selected>0) {

                    var keyIterator = this.serverSocketSelector.selectedKeys.iterator;
                    while (keyIterator.hasNext) {

                        var key = keyIterator.next();
                        key match {


                            // Accept
                            //--------------------
                            case key if (key.isValid && key.isAcceptable) => {

                                //println("Accepting Connection")

                                var clientSocket = serverSocket.accept



                                // Prepare Network Context
                                //----------------------------
                                var networkContext = new TCPNetworkContext(clientSocket)
                                clientsContextsMap += (networkContext.toString -> networkContext)

                                @->("server.accepted")
                                @->("server.accepted",networkContext)

                                // Register Socket Channel to selector
                                // !! Selector only works on non blocking sockets
                                //-----------------
                                clientSocket.configureBlocking(false);
                                var clientSocketKey = clientSocket.register(this.serverSocketSelector,SelectionKey.OP_READ,SelectionKey.OP_WRITE)

                                //-- Register NetworkContext with key
                                clientSocketKey.attach(networkContext)



                            }

                            // Read: Pass Read datas to underlying implementation
                            //-----------------
                            case key if (key.isValid && key.isReadable) => {


                                @->("server.read")

                                //-- Take Channel
                                var networkContext = key.attachment().asInstanceOf[TCPNetworkContext]
                                var socketChannel = networkContext.socket

                                // Read Until 0, or < 0 closes the channel
                                //----------
                                var continue = true;
                                while (continue)
                                    socketChannel.read(readBuffer) match {

                                        // Continue Reading
                                        //------------
                                        case readbytes if (readbytes > 0) => {

                                            // Pass Datas to underlying protocol
                                            readBuffer.flip

                                            var passedBuffer = ByteBuffer.allocate(readbytes)
                                            passedBuffer.put(readBuffer)
                                            passedBuffer.rewind

                                            //readBuffer.clear
                                            protocolReceiveData(passedBuffer,networkContext)


                                            // Clear Buffer for next read
                                            readBuffer.clear();
                                        }

                                        // Nothing to read
                                        //----------------
                                        case readbytes if (readbytes == 0) => {
                                            continue = false
                                        }

                                        // Close Client Connection
                                        //------------
                                        case readbytes if (readbytes < 0) => {
                                            this.clientsContextsMap -= networkContext.toString
                                            socketChannel.close();
                                            continue = false
                                        }
                                    }

                            }

                            // Fall back
                            //----------------
                            case key => {

                            }

                        }
                        keyIterator.remove

                    }
                //}
                // EOF if selected > 0

                } catch {

                    case e : java.nio.channels.ClosedSelectorException => 

                    case e : Throwable => 
                       
                }

            } // EOF Server thread loop

            // Clean
            //----------------
            this.serverSocketSelector = null 

            this.serverSocket.close
            this.serverSocket = null

            @->("server.end")
            
        }
        // Client Mode
        //------------------------
        else {


            logInfo(s"Starting TCP Connector as client on $address:$port")

            // Connect
            this.clientSocket = SocketChannel.open(new InetSocketAddress(this.address,this.port))

            // Record Network Context
            this.clientNetworkContext = new TCPNetworkContext(this.clientSocket)

            logInfo(s"Client Started")

            @->("client.started")
            @->("common.started")

            // Data Loop
            //------------------------
            var continue = true;
            while (continue && !this.stopThread)
                this.clientSocket.read(readBuffer) match {

                    // Continue Reading
                    //------------
                    case readbytes if (readbytes > 0) => {

                        // Pass Datas to underlying protocol
                        readBuffer.flip

                        var passedBuffer = ByteBuffer.allocate(readbytes)
                        passedBuffer.put(readBuffer)
                        passedBuffer.rewind

                        protocolReceiveData(passedBuffer,this.clientNetworkContext)


                        // Clear Buffer for next read
                        readBuffer.clear();
                    }

                    // Close Client Connection
                    //------------
                    case readbytes if (readbytes < 0) => {
                        this.clientSocket.close();
                        continue = false
                    }
                }

        }
    }


}


/**
    This class is an implementation of TCPConnector, handing out application protocol management to a Protocolhandler class

*/
abstract class TCPProtocolHandlerConnector( var protocolHandlerFactory : ( TCPNetworkContext => ProtocolHandler[ByteBuffer]) ) extends TCPConnector {



    // Protocol Implementation
    //----------------
    def protocolReceiveData(buffer : ByteBuffer,context: TCPNetworkContext) : Unit = {

        // Receive through Protocol handler
        //---------------
        ProtocolHandler(context,protocolHandlerFactory).receive(buffer)

    }

    def protocolSendData(buffer : ByteBuffer,context: TCPNetworkContext) : Unit  = {


        ProtocolHandler(context,protocolHandlerFactory).send(buffer)


    }

}


// Network Context
//---------------------
 class TCPNetworkContext(var socket : SocketChannel ) extends NetworkContext {



}
