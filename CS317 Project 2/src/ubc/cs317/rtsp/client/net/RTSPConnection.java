/*
 * University of British Columbia
 * Department of Computer Science
 * CPSC317 - Internet Programming
 * Assignment 2
 * 
 * Author: Jonatan Schroeder
 * January 2013
 * 
 * This code may not be used without written consent of the authors, except for 
 * current and future projects and assignments of the CPSC317 course at UBC.
 */

package ubc.cs317.rtsp.client.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;

import ubc.cs317.rtsp.client.exception.RTSPException;
import ubc.cs317.rtsp.client.model.Frame;
import ubc.cs317.rtsp.client.model.Session;

/**
 * This class represents a connection with an RTSP server.
 */
public class RTSPConnection {

	private static final int BUFFER_LENGTH = 15000;
	private static final long MINIMUM_DELAY_READ_PACKETS_MS = 20;
	private static final int DGSOCKET_PORT = 25000;
	private Session session;
	private Timer rtpTimer;
	
	// TODO Add additional fields, if necessary
	private Socket socket;
	private int cSeq;
	private int sessionID;
	private String video;
	private BufferedReader RTSPReader;
	private BufferedWriter RTSPWriter;
	private DatagramSocket datagramSocket;

	/**
	 * Establishes a new connection with an RTSP server. No message is sent at
	 * this point, and no stream is set up.
	 * 
	 * @param session
	 *            The Session object to be used for connectivity with the UI.
	 * @param server
	 *            The hostname or IP address of the server.
	 * @param port
	 *            The TCP port number where the server is listening to.
	 * @throws RTSPException
	 *             If the connection couldn't be accepted, such as if the host
	 *             name or port number are invalid or there is no connectivity.
	 */
		public RTSPConnection(Session session, String server, int port)
				throws RTSPException {

		this.session = session;
		
		try {
			socket = new Socket(server, port);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			throw new RTSPException(e);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RTSPException(e);
		}
		
	}

	/**
	 * Sends a SETUP request to the server. This method is responsible for
	 * sending the SETUP request, receiving the response and retrieving the
	 * session identification to be used in future messages. It is also
	 * responsible for establishing an RTP datagram socket to be used for data
	 * transmission by the server. The datagram socket should be created with a
	 * random UDP port number, and the port number used in that connection has
	 * to be sent to the RTSP server for setup. This datagram socket should also
	 * be defined to timeout after 1 second if no packet is received.
	 * 
	 * @param videoName
	 *            The name of the video to be setup.
	 * @throws RTSPException
	 *             If there was an error sending or receiving the RTSP data, or
	 *             if the RTP socket could not be created, or if the server did
	 *             not return a successful response.
	 */
	public synchronized void setup(String videoName) throws RTSPException {
		cSeq = 1;
		this.video = videoName;
		String request = "SETUP "+video+" RTSP/1.0\n";
		String add1 = "CSeq: "+cSeq+"\n";
		String add2 = "Transport: RTP/UDP; client_port= "+DGSOCKET_PORT+"\n";
		try {
			RTSPReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			RTSPWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			RTSPWriter.write(request);
			RTSPWriter.write(add1);
			RTSPWriter.write(add2);
			RTSPWriter.write("\n");
			RTSPWriter.flush();
			System.out.println("hi");
			RTSPResponse rtspResp = RTSPResponse.readRTSPResponse(RTSPReader);
			System.out.println("Response Code: "+rtspResp.getResponseCode()+"\n");
			System.out.println("Response Message: "+rtspResp.getResponseMessage()+"\n");
			if(rtspResp.getResponseCode()!=200||!rtspResp.getResponseMessage().equals("OK")){
				throw new RTSPException("The server did not return a successful response.");
			}
			else
				sessionID = rtspResp.getSessionId();
			
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			throw new RTSPException(e1);
		
		}
		
		try {
			datagramSocket = new DatagramSocket(DGSOCKET_PORT);
			datagramSocket.setSoTimeout(1000);
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// TODO
	}

	/**
	 * Sends a PLAY request to the server. This method is responsible for
	 * sending the request, receiving the response and, in case of a successful
	 * response, starting the RTP timer responsible for receiving RTP packets
	 * with frames.
	 * 
	 * @throws RTSPException
	 *             If there was an error sending or receiving the RTSP data, or
	 *             if the server did not return a successful response.
	 */
	public synchronized void play() throws RTSPException {
		cSeq++;
		String request = "PLAY "+video+" RTSP/1.0\n";
		String add1 = "CSeq: "+cSeq+"\n";
		String add2 = "Session: "+sessionID+"\n";

		try {
			RTSPReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			RTSPWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			RTSPWriter.write(request);
			RTSPWriter.write(add1);
			RTSPWriter.write(add2);
			RTSPWriter.write("\n");
			RTSPWriter.flush();
			System.out.println("2");
			RTSPResponse rtspResp = RTSPResponse.readRTSPResponse(RTSPReader);
			System.out.println("Response Code: "+rtspResp.getResponseCode()+"\n");
			System.out.println("Response Message: "+rtspResp.getResponseMessage()+"\n");
			if(rtspResp.getResponseCode()!=200||!rtspResp.getResponseMessage().equals("OK")){
				throw new RTSPException("The server did not return a successful response.");
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			throw new RTSPException(e1);
		
		}
		startRTPTimer();
	}

	/**
	 * Starts a timer that reads RTP packets repeatedly. The timer will wait at
	 * least MINIMUM_DELAY_READ_PACKETS_MS after receiving a packet to read the
	 * next one.
	 */
	private void startRTPTimer() {

		rtpTimer = new Timer();
		rtpTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				receiveRTPPacket();
			}
		}, 0, MINIMUM_DELAY_READ_PACKETS_MS);
	}

	/**
	 * Receives a single RTP packet and processes the corresponding frame. The
	 * data received from the datagram socket is assumed to be no larger than
	 * BUFFER_LENGTH bytes. This data is then parsed into a Frame object (using
	 * the parseRTPPacket method) and the method session.processReceivedFrame is
	 * called with the resulting packet. In case of timeout no exception should
	 * be thrown and no frame should be processed.
	 */
	private void receiveRTPPacket() {
		
		byte[] buffer = new byte[BUFFER_LENGTH];
		
		DatagramPacket RTPPacket = new DatagramPacket(buffer,BUFFER_LENGTH);
		try {
			datagramSocket.receive(RTPPacket);
			session.processReceivedFrame(parseRTPPacket(buffer));
			
		} catch (IOException e) {
			// do nothing
		}
		// TODO
	}

	/**
	 * Sends a PAUSE request to the server. This method is responsible for
	 * sending the request, receiving the response and, in case of a successful
	 * response, cancelling the RTP timer responsible for receiving RTP packets
	 * with frames.
	 * 
	 * @throws RTSPException
	 *             If there was an error sending or receiving the RTSP data, or
	 *             if the server did not return a successful response.
	 */
	public synchronized void pause() throws RTSPException {
		cSeq++;
		String request = "PAUSE "+video+" RTSP/1.0\n";
		String add1 = "CSeq: "+cSeq+"\n";
		String add2 = "Session: "+sessionID+"\n";

		try {
			RTSPReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			RTSPWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			RTSPWriter.write(request);
			RTSPWriter.write(add1);
			RTSPWriter.write(add2);
			RTSPWriter.write("\n");
			RTSPWriter.flush();
			System.out.println("2");
			RTSPResponse rtspResp = RTSPResponse.readRTSPResponse(RTSPReader);
			System.out.println("Response Code: "+rtspResp.getResponseCode()+"\n");
			System.out.println("Response Message: "+rtspResp.getResponseMessage()+"\n");
			if(rtspResp.getResponseCode()!=200||!rtspResp.getResponseMessage().equals("OK")){
				throw new RTSPException("The server did not return a successful response.");
			}
			rtpTimer.cancel();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			throw new RTSPException(e1);
		
		}
	}

	/**
	 * Sends a TEARDOWN request to the server. This method is responsible for
	 * sending the request, receiving the response and, in case of a successful
	 * response, closing the RTP socket. This method does not close the RTSP
	 * connection, and a further SETUP in the same connection should be
	 * accepted. Also this method can be called both for a paused and for a
	 * playing stream, so the timer responsible for receiving RTP packets will
	 * also be cancelled.
	 * 
	 * @throws RTSPException
	 *             If there was an error sending or receiving the RTSP data, or
	 *             if the server did not return a successful response.
	 */
	public synchronized void teardown() throws RTSPException {
		cSeq++;
		String request = "TEARDOWN "+video+" RTSP/1.0\n";
		String add1 = "CSeq: "+cSeq+"\n";
		String add2 = "Session: "+sessionID+"\n";

		try {
			RTSPReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			RTSPWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			RTSPWriter.write(request);
			RTSPWriter.write(add1);
			RTSPWriter.write(add2);
			RTSPWriter.write("\n");
			RTSPWriter.flush();
			System.out.println("2");
			RTSPResponse rtspResp = RTSPResponse.readRTSPResponse(RTSPReader);
			System.out.println("Response Code: "+rtspResp.getResponseCode()+"\n");
			System.out.println("Response Message: "+rtspResp.getResponseMessage()+"\n");
			if(rtspResp.getResponseCode()!=200||!rtspResp.getResponseMessage().equals("OK")){
				throw new RTSPException("The server did not return a successful response.");
			}
			rtpTimer.cancel();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			throw new RTSPException(e1);
		
		}
	}

	/**
	 * Closes the connection with the RTSP server. This method should also close
	 * any open resource associated to this connection, such as the RTP
	 * connection, if it is still open.
	 */
	public synchronized void closeConnection() {
		try {
			socket.close();
			datagramSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	/**
	 * Parses an RTP packet into a Frame object.
	 * 
	 * @param packet
	 *            the byte representation of a frame, corresponding to the RTP
	 *            packet.
	 * @return A Frame object.
	 */
	private static Frame parseRTPPacket(byte[] packet) {
		byte tempByte = packet[1];
		boolean marker =( tempByte&(1<<0))!=0;
		byte pt = (byte) (packet[1]&0x0fffffff);
		short sn = (short) ((packet[2]<<8) | (packet[3]));
		int ts = (packet[4] << 24 | (packet[5] & 0xFF) << 16 | (packet[6] & 0xFF) << 8 | (packet[7] & 0xFF));
		
		System.out.println("Payload Type: "+pt+"\n");
		System.out.println("Sequence Number: "+sn+"\n");
		System.out.println("Timestamp: "+ts+"\n");

		Frame frame = new Frame(pt, marker, sn, ts, packet, 12, packet.length-12);
		/*
		 * 1=pt
		 * 2=?
		 * 3=seqnumber
		 * 4
		 * 5
		 * 6=
		 */
		
		System.out.println("NEW PACKETS\n");
		// TODO
		return frame; // Replace with a proper Frame
	}
}
