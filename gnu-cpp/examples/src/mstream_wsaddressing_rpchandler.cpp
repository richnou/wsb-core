/**
 * wsaddressing_mstream.cpp
 *
 * This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. 
 * To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/ or send 
 * a letter to Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.  
 *
 *
 *
 *  This example does following:
 *
 *  - Setup a WSEngine with:
 *
 *     * Network Layer Connector: TCPMessageStream (TCP connection with simple Message extraction protocol layer)
 *     * BrokeringTree:
 *        |- WSAddressing Intermediary for message qualifier
 *          |-RPCHandlerIntermediray to register Handler factory for specific message actions
 *
 *  - Fork 1 Server
 *  - Forks 3 Clients to show functionality
 *
 *
 *  The server implements an Echo Back function:
 *
 *  - Client Sends <EchoRequest pid="...pidnumber...">AStringToBeEchoed</EchoRequest>
 *  - Server Answers: <EchoResponse>Echo "AStringToBeEchoed" to Process pidnumber</EchoResponse>
 *
 */

// Includes
//------------------
//-- Std
#include <iostream>
using namespace std;

//-- WSB Core
#include <wsb-core/engine/WSEngine.h>
#include <wsb-core/common/Logging.h>

//-- WSB Std Lib
#include <wsb-libstd/connector/tcp/MessageStreamTCPConnector.h>
#include <wsb-libstd/intermediary/WSAddressing.h>
#include <wsb-libstd/message/soap/SOAPMessage.h>

//-- WSB Remote Lib
#include <wsb-libremote/intermediaries/callup/RPCHandlerIntermediary.h>

using namespace OSI::WSB;
using namespace OSI::WSB::LIBREMOTE;

/// Method to implement child Process behaviour
void childProcess();
void serverProcess();
/**
 *
 * This class could be generated by parsing comments
 *
 */
class EchoHandler : public RPCHandler {


    public:
        EchoHandler() {

        }

        virtual ~EchoHandler() {

        }

        /**
         * WARNING:
         *
         * WE use the same handler definition for Request/Response, so we check in here the body payload name to know in which case we are
         *
         * @param context
         * @return
         */
        Message * handle(MessageContext * context) {


            SOAPMessage * requestSoap = dynamic_cast<SOAPMessage*>(context->getMessage());

            // getBody = S:Body, the "EchoRequest/Response" payload is the first child of S:Body
            // -> S:Body/example:EchoRequest/Response
            DOMElement * payload = requestSoap->getBody()->getFirstElementChild();

            // Request
            //---------------------
            if (string(UTF8(payload->getLocalName())) == "EchoRequest") {

                cout<< "******* EchoRequest (Server) *******" << endl;

                requestSoap->addXPathNamespace("example","com:idyria:osi:examples");

                // Get PID
                //-------------
                //int pidnumber =requestSoap->getBase()->getInteger("./@pid",payload);
                //string pidnumber =requestSoap->getBase()->getString("./@pid",payload);

                string pidnumber = UTF8(payload->getAttribute(X("pid")));


                // Prepare Response Message
                //-------------

                //-- Set Response payload
                stringstream ss;
                ss << "Echo \""<< UTF8(payload->getTextContent())  <<"\" to Process "<< pidnumber;

                SOAPMessage * soap = new SOAPMessage();
                soap->addBodyPayload("com:idyria:osi:examples","example:EchoResponse",ss.str());

                //-- Use same Qualifier (message action) for response
                soap->setQualifier(requestSoap->getQualifier());

                return soap;

            } else {

                cout<< "******* EchoResponse (Client) *******" << endl;
                cout<< "Server says: " << UTF8(payload->getTextContent()) << endl;


                exit(0);

                return NULL;

            }


        }


};

/**
 * This class implements RPCHandlerFactory to create the handler for the various supported requests in this example
 *
 * This class could be generated by parsing comments
 *
 */
class ExampleRPCHandlerFactory : public RPCHandlerFactory {

    public:
        ExampleRPCHandlerFactory() {

        }

        virtual ~ExampleRPCHandlerFactory() {

        }

        virtual RPCHandler * newInstance(MessageContext * context) {

            if (context->getMessage()->getQualifier() == "example.echo") {

                    return new EchoHandler();

            }

            return NULL;
        }


};



int main(int argc, char**argv) {


    if (argc>1) {



        if (strcmp(argv[1],"-server")==0) {

            serverProcess();

        } else if (strcmp(argv[1],"-client")==0) {

            childProcess();

        }


    } else {

        // ForK:
        // - 3 Children
        // - 1 Server
        pid_t childPid = fork();
        if (childPid == 0) {



            sleep(2);

            if (fork()) {

                if (fork()) {

                    // Child3
                    //----------------------------
                   // childProcess();

                } else {

                    // Child2
                    //----------------------------
                    //childProcess();
                }

            } else {

                // Child1
                //----------------------------
                childProcess();
            }

        } else {

            serverProcess();

        }

    }


    // Create WSEngine
    //---------------------

    // Create and Add MessageStreamConnector

    return 0;
}

void serverProcess() {

    // Forked Server
    //----------------------------

    //-- Create WSEngine
    WSEngine * engine = new WSEngine();
    engine->init();

    //-- Network
    //------------------

    //-- Add MessageStream Connector and support SOAP
    MessageStreamTCPConnector * mstreamConnector = new MessageStreamTCPConnector();
    mstreamConnector->setMessageType("soap");
    engine->getNetwork().addConnector(mstreamConnector);

    //-- Brokering
    //----------------------
    engine->getBroker().setBrokeringTree(new BrokeringTree());

    //-- Add WS-Addressing Intermediary
    WSAddressing * wsadressing = new WSAddressing();
    engine->getBroker().getBrokeringTree()->addChild(wsadressing);

    //-- Add RPC Handler as sink for messages
    RPCHandlerIntermediary * rpcintermediary = new RPCHandlerIntermediary();
    wsadressing->addChild(rpcintermediary);

    //-- RPCHandler
    //----------------------

    //-- Register RPCHandler factory to support:
    //    - EchoRequest/Response
    RPCHandlerFactory * handlersFactory = new ExampleRPCHandlerFactory();
    rpcintermediary->registerHandlerFactory("example.echo",handlersFactory);


    //-- Start
    //---------------
    engine->start();

    cout << "Server Started" << endl;
    int stop = 0;
    cin >> stop;

    cout << "Server Stopped" << endl;
    engine->stop();
    engine->finish();


}


void childProcess() {


    pid_t pid = getpid();
    cout << "[PID:"<< pid <<"] Child Started" << endl;


    Logging::getLogger("OSI.wsb")->setPriority(21000);

    //-- Create WSEngine
    WSEngine * engine = new WSEngine();
    engine->init();

    //-- Network
    //------------------

    //-- Register MessageStream Connector as Client and support SOAP
    MessageStreamTCPConnector * mstream = new MessageStreamTCPConnector();
    mstream->setDirection(AbstractConnector::CLIENT);
    mstream->setMessageType("soap");
    engine->getNetwork().addConnector(mstream);

    //-- Brokering
    //----------------------
    engine->getBroker().setBrokeringTree(new BrokeringTree());

    //-- Add WS-Addressing Intermediary
    WSAddressing * wsadressing = new WSAddressing();
    engine->getBroker().getBrokeringTree()->addChild(wsadressing);

    //-- Add RPC Handler as sink for messages
    RPCHandlerIntermediary * rpcintermediary = new RPCHandlerIntermediary();
    wsadressing->addChild(rpcintermediary);

    //-- Register RPCHandler factory to support:
    //    - EchoRequest/Response
    // !! We are here in client, so incoming handled messages will be the responses
    RPCHandlerFactory * handlersFactory = new ExampleRPCHandlerFactory();
    rpcintermediary->registerHandlerFactory("example.echo",handlersFactory);


    // Start Engine
    //--------------------
    engine->start();

    // Send ECHO
    //--------------------------------------

    //-- Prepare Message
    //-----------------------
    SOAPMessage * soap = new SOAPMessage();

    soap->setQualifier("example.echo");

    //-- Add WSAddressing Header
    /*soap->addXPathNamespace("wsa",
                "http://www.w3.org/2005/08/addressing");
    soap->addHeader("http://www.w3.org/2005/08/addressing", "wsa:Action",
                "example.echo");*/

    //-- Request
    DOMElement * payload = soap->addBodyPayload("com:idyria:osi:examples","example:EchoRequest","Howwwdyyy");
    stringstream ss;
    //ss << pid;
    //payload->setAttribute(X("pid"),X(ss.str().c_str()));
    payload->setAttribute(X("pid"),X("0"));

    //-- Prepare Network Context for sending
    //------------------
    NetworkContext * networkContext = new NetworkContext("mstream://127.0.0.1");

    //-- Send through broker
    //-------------------------



    //-- Create Message Context
    MessageContext * msg = new MessageContext(soap,networkContext);
    engine->getBroker().send(&(engine->getNetwork()),msg);



    //cout << "[PID:"<< pid <<"] Send: " << soap->toString() << endl;



    int stop = 0;
    cin >> stop;

    cout << "[PID:"<< pid <<"] Child Stopped" << endl;



}

