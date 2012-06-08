/*
    Copyright (c) 2012 Thomas Zink, 

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.fhkn.in.uce.reversal.source;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fhkn.in.uce.messages.CommonUceMethod;
import de.fhkn.in.uce.messages.SemanticLevel;
import de.fhkn.in.uce.messages.SocketEndpoint;
import de.fhkn.in.uce.messages.SocketEndpoint.EndpointClass;
import de.fhkn.in.uce.messages.UceMessage;
import de.fhkn.in.uce.messages.UceMessageReader;
import de.fhkn.in.uce.messages.UceMessageStaticFactory;
import de.fhkn.in.uce.messages.UniqueUserName;

/**
 * Class that implements the source of the connection reversal.
 * 
 * The Class provides methods to connect to the target of the configuration.
 * There is also the possibility to connect using a special port and to get
 * the whole list of registered targets at the mediator.
 * 
 * @author thomas zink, stefan lohr
 */
public class ConnectionReversalSource {
	private static final Logger logger = LoggerFactory.getLogger(ConnectionReversalSource.class);
	
	private DatagramSocket datagramSocket;
	private SocketAddress mediatorSocketAddress;
	
	//private String uniqueUserName;
	//private boolean requested;
		
	//public ConnectionReversalSource(String uniqueUserName, String mediatorIP, int mediatorPort) {
	public ConnectionReversalSource(String mediatorIP, int mediatorPort) {
		//this.uniqueUserName = uniqueUserName;
		try {
			
			mediatorSocketAddress = new InetSocketAddress(mediatorIP, mediatorPort);
			datagramSocket = new DatagramSocket();
		}
		catch (SocketException e) {
			logger.error(e.getMessage());
		}
	}
	
	/**
	 * Method to request a connection. Creates a server socket the source
	 * listens and waits for a connection. Uses a random port the target can 
	 * connects to. 
	 * 
	 * @return ServerSocket the target connects to.
	 * @throws Exception
	 */
	private ServerSocket requestConnection(String uniqueUserName) throws SocketTimeoutException, IOException {
		return requestConnection(uniqueUserName, 0);
	}
	
	/**
	 * Method to request a connection. Creates a server socket the source
	 * listens and waits for a connection. The listen port is specified as
	 * argument. By 0 as port number, a random port is used.
	 * 
	 * @param port the port for target connection
	 * @return ServerSocket the target connects to.
	 * @throws IOException
	 * @throws SocketTimeoutException
	 */
	private ServerSocket requestConnection(String uniqueUserName, int port) throws SocketTimeoutException, IOException {
		//if (requested) throw new IllegalStateException("already requested");
		//else requested = true;
		
		logger.info("initialize local server socket");
		
		ServerSocket serverSocket = new ServerSocket(port);
		InetAddress sourceAddress = serverSocket.getInetAddress();
		int sourcePort = serverSocket.getLocalPort();

		InetSocketAddress endpoint = new InetSocketAddress(sourceAddress, sourcePort);
		
		UceMessage uceConnectionRequestMessage = UceMessageStaticFactory.newUceMessageInstance(
				CommonUceMethod.CONNECTION_REQUEST, SemanticLevel.REQUEST, UUID.randomUUID());
		
		uceConnectionRequestMessage.addAttribute(new UniqueUserName(uniqueUserName));
		
		EndpointClass endpointClass = SocketEndpoint.EndpointClass.CONNECTION_REVERSAL;
		SocketEndpoint socketEndpoint = new SocketEndpoint(endpoint, endpointClass);
		
		uceConnectionRequestMessage.addAttribute(socketEndpoint);
		
		byte[] buf = uceConnectionRequestMessage.toByteArray();
		
		DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length, mediatorSocketAddress);
		
		logger.info("send connection request");
		datagramSocket.send(datagramPacket);
		
		serverSocket.setSoTimeout(10000);
		
		return serverSocket;
	}
	
	public Socket connect(String uniqueUserName) {
		ServerSocket serverSocket = null;
		Socket socket = null;
		
		try {
			serverSocket = requestConnection(uniqueUserName);
		} catch (SocketTimeoutException e2) {
			logger.error(e2.getMessage());
		} catch (IOException e2) {
			logger.error(e2.getMessage());
		}
		
		logger.info("listen on serverSocket {}", serverSocket);
		
		try {	
			socket = serverSocket.accept();
		} catch (SocketTimeoutException e1) {
			logger.error(e1.getMessage());
		} catch (IOException e) {
			logger.error(e.getMessage());
		} finally {
			try {
				serverSocket.close();
			} catch (IOException e) {
				logger.error(e.getMessage());
			}
		}
		
		logger.info("connection established");
		return socket;
	}
	
	/*public Socket accept() {
		
		logger.info("listen on serverSocket {}:{}", sourceAddress, sourcePort);
		
		Socket socket;
		
		try {
			socket = serverSocket.accept();
		}
		catch (SocketTimeoutException e) {
			serverSocket.setSoTimeout(0);
			throw new SocketTimeoutException();
		}
		finally {
			serverSocket.close();
		}
		
		logger.info("connection established");
		return socket;
	}*/
	
	
	
	/**
	 * Returns a set of Strings of all users are registered on Mediator
	 * 
	 * @return Set of Strings with registered users on Mediator
	 * @throws IOException
	 */
	public Set<String> getUserList() throws IOException {
		
		UceMessage uceListMessage = UceMessageStaticFactory.newUceMessageInstance(
				CommonUceMethod.LIST, SemanticLevel.REQUEST, UUID.randomUUID());
		
		byte[] requestBuffer = uceListMessage.toByteArray();
		
		/*
		 * TODO: paket shouldn't exceed 512 Bytes, what if too many users?
		 * see mediator
		 */
		DatagramPacket datagramPacketSend = new DatagramPacket(requestBuffer, requestBuffer.length, mediatorSocketAddress);
		DatagramPacket datagramPacketReceive = new DatagramPacket(new byte[65536], 65536);
		
		logger.info("send request for userList");
		datagramSocket.send(datagramPacketSend);
		logger.info("waiting for userList");
		
		datagramSocket.setSoTimeout(10000);
		try {
			
			datagramSocket.receive(datagramPacketReceive);
		}
		catch (SocketTimeoutException ste) {
			
			logger.error("no userList received");
			ste.printStackTrace();
			
			return new HashSet<String>();
		}
		finally {
			
			datagramSocket.setSoTimeout(0);
		}
		
		logger.info("userList received, generate return value");
		
		byte[] userListBuffer = datagramPacketReceive.getData();
		
		UceMessageReader uceMessageReader = new UceMessageReader();
		UceMessage uceMessage = uceMessageReader.readUceMessage(userListBuffer);
		
		List<UniqueUserName> uniqueUserNameList = uceMessage.getAttributes(UniqueUserName.class);
		Set<String> userList = new HashSet<String>();
		
		for (UniqueUserName uniqueUserName : uniqueUserNameList) {
			userList.add(uniqueUserName.getUniqueUserName());
		}
		
		return userList;
	}
}