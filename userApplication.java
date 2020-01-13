// Δίκτυα Υπολογιστών ΙΙ Χαμεζόπουλος Σάββας

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Scanner;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class Main {

	public static void main(String[] args) {

		boolean flag = true;
		Scanner ins = new Scanner(System.in);

		System.out.println("Welcome!");

		// insert Server listening port
		int serverPort = 38012;
		System.out.print("Please insert Server listening port: (38000 - 38032) for echo, image, sound"
				+ "\n\t\t\t\t\t       29078 for OBD-II" + "\n\t\t\t\t\t	38048 for Ithakikopter");
		do {
			serverPort = ins.nextInt();
			if (serverPort >= 38000 && serverPort <= 38032 || serverPort == 29078 || serverPort == 38048)
				flag = false;
			else {
				System.out.println("ERROR: please insert a valid option\n");
			}
		} while (flag);

		// insert Client listening port
		int clientPort = 48012;
		System.out.print("Please insert Client listening port: (48000 - 48032)");
		flag = true;
		do {
			clientPort = ins.nextInt();
			if (clientPort >= 48000 && clientPort <= 48032)
				flag = false;
			else {
				System.out.println("ERROR: please insert a valid option\n");
			}
		} while (flag);

		int choice = 0;

		for (;;) {
			System.out.println("Please choose one option:");
			System.out.println("Press:");
			System.out.println("For Echo (w/ and w/o delays: 1");
			System.out.println("For Image: 2");
			System.out.println("For OBD-II: 3");
			System.out.println("For Ithakikopter: 4");
			System.out.println("For Sound using DPCM: 5");
			System.out.println("For Sound using AQ-DPCM: 6");
			System.out.println("O (zero) to quit");
			System.out.print("Insert Option: ");

			do {

				choice = ins.nextInt();
				if (choice >= 0 && choice <= 6)
					flag = false;
				else {
					System.out.println("ERROR: please insert a valid option (0,1,2,3,4,5,6)\n");
				}
			} while (flag);

			flag = true;

			if (choice == 0) {
				System.out.println("Exiting...");
				break;
			} else if (choice == 1)
				(new Main()).echo(ins, clientPort, serverPort);
			else if (choice == 2)
				(new Main()).image(ins, clientPort, serverPort);
			else if (choice == 3)
				(new Main()).obdII(29078);
			else if (choice == 4)
				(new Main()).ithakicopter(ins, 38048);
			else if (choice == 5)
				(new Main()).DPCM(ins, clientPort, serverPort);
			else if (choice == 6)
				(new Main()).AQ_DPCM(ins, clientPort, serverPort);
		}
		ins.close();
	}

	public void echo(Scanner ins, int clientPort, int serverPort) {

		System.out.println("\nInitializing Echo w/ random delays.");

		// create Datagram Socket s
		DatagramSocket s = null;
		try {
			s = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}

		// find host
		byte[] hostIP = { (byte) 155, (byte) 207, (byte) 18, (byte) 208 };
		InetAddress hostAddress = null;
		try {
			hostAddress = InetAddress.getByAddress(hostIP);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		// create DataGram Socket r
		DatagramSocket r = null;
		try {
			r = new DatagramSocket(clientPort);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		try {
			r.setSoTimeout(10000);
		} catch (SocketException e) {
			e.printStackTrace();
		}

		// insert echo_request_code EXXXX
		System.out.print("Please insert int part of echo_request_code EXXXX:");
		int echoRequestCode = 0;

		boolean flag = true;
		do {
			echoRequestCode = ins.nextInt();
			if (echoRequestCode >= 1000 && echoRequestCode <= 9999)
				flag = false;
			else {
				System.out.println("ERROR: please insert 4-digit integer\n");
			}
		} while (flag);

		// create txbuffer
		String packetInfo = "E" + Integer.toString(echoRequestCode);
		byte[] txbuffer = packetInfo.getBytes();
		DatagramPacket p = new DatagramPacket(txbuffer, txbuffer.length, hostAddress, serverPort);

		// create rxbuffer
		byte[] rxbuffer = new byte[2048];
		DatagramPacket q = new DatagramPacket(rxbuffer, rxbuffer.length);

		// 5 minute loop init
		long startTime = System.currentTimeMillis(), endTime = 0; // for controlling connection time
		long startLoopTime = 0, endLoopTime = 0; // for sampling package transfer time
		int sampleNo = 0;
		long startMovingAverage = System.currentTimeMillis(), endMovingAverage = 0;
		ArrayList<Integer> checkpoints = new ArrayList<Integer>();
		float interval = 16;
		long sample = 0; // to save Sample
		int loopCount = 0;

		// Files creation/opening
		BufferedWriter bw = null;
		try {

			File file = new File("echo.txt");
			if (!file.exists())
				file.createNewFile();

			FileWriter wr = new FileWriter(file);
			bw = new BufferedWriter(wr);

		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		// ArrayList to keep temps
		ArrayList<String> t = new ArrayList<String>();

		String message = "N/A";
		int i = 0;

		System.out.println("Loops initializing...");
		// ~ 5 minute loop
		do {

			startLoopTime = System.currentTimeMillis();
			// also take the temperature for the TXX station for the first sample
			if (i < 1) {
				packetInfo = "E" + Integer.toString(echoRequestCode) + "T0" + Integer.toString(i);
				i++;
			} else {
				packetInfo = "E" + Integer.toString(echoRequestCode);
				i++;
			}
			txbuffer = packetInfo.getBytes();
			p = new DatagramPacket(txbuffer, txbuffer.length, hostAddress, serverPort);
			try {
				s.send(p);
			} catch (IOException e) {
				e.printStackTrace();
			}

			try {
				r.receive(q);
				sampleNo++;
				message = new String(rxbuffer, 0, q.getLength());
				t.add(message);
				// System.out.println(message);
				endLoopTime = System.currentTimeMillis();
				endMovingAverage = System.currentTimeMillis();
				if((endMovingAverage - startMovingAverage)/1000.0 >= interval) {
					checkpoints.add(sampleNo);
					startMovingAverage = System.currentTimeMillis();
				}
				sample = endLoopTime - startLoopTime;
			} catch (Exception x) {
				System.out.println(x);
			}

			// write to file
			try {
				if (bw != null)
					bw.write(sample + "\n");
			} catch (Exception wr) {
				break;
			}

			// Just to see program is running fine
			loopCount++;
			if (loopCount % 10 == 0)
				System.out.println("CountLoop:" + loopCount);

			endTime = System.currentTimeMillis();
		} while ((endTime - startTime) / 1000.0 < 5 * 60);

		try {
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		bw = null;
		try {
			File file = new File("echo_temps.txt");
			if (!file.exists()) {
				file.createNewFile();
			}
			FileWriter fw = new FileWriter(file);
			bw = new BufferedWriter(fw);
			for (int n = 0; n < i; n++) {
				bw.write(t.get(n) + "\n");
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		try {
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		store_to_file_Integer("checkPointsDelays", checkpoints);
		
		System.out.println("Temps received and saved.");

		System.out.println("\nInitializing Echo w/o random Delays: ");

		packetInfo = "E0000";
		txbuffer = packetInfo.getBytes();
		p = new DatagramPacket(txbuffer, txbuffer.length, hostAddress, serverPort);

		// File creation/opening
		bw = null;
		try {
			File file = new File("echo_with_out_delay.txt");
			if (!file.exists())
				file.createNewFile();

			FileWriter wr = new FileWriter(file);
			bw = new BufferedWriter(wr);

		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		startTime = System.currentTimeMillis();
		endTime = 0;
		loopCount = 0;
		sampleNo = 0;
		startMovingAverage = System.currentTimeMillis();
		checkpoints = new ArrayList<Integer>();

		// ~ 5 minute loop
		do {

			startLoopTime = System.currentTimeMillis();
			try {
				s.send(p);
			} catch (IOException e) {
				e.printStackTrace();
			}

			try {
				r.receive(q);
				sampleNo++;
				message = new String(rxbuffer, 0, q.getLength());
				t.add(message);
				// System.out.println(message);
				endLoopTime = System.currentTimeMillis();
				endMovingAverage = System.currentTimeMillis();
				if((endMovingAverage - startMovingAverage)/1000.0 >= interval) {
					checkpoints.add(sampleNo);
					startMovingAverage = System.currentTimeMillis();
				}
				sample = endLoopTime - startLoopTime;
			} catch (Exception x) {
				System.out.println(x);
			}

			// write to file
			try {
				if (bw != null)
					bw.write(sample + "\n");
			} catch (Exception wr) {
				break;
			}

			// Just to see program is running fine
			loopCount++;
			if (loopCount % 100 == 0)
				System.out.println("CountLoop:" + loopCount);

			endTime = System.currentTimeMillis();
		} while ((endTime - startTime) / 1000.0 < 5 * 60);

		System.out.println("Echo w/o delays received and saved.");
		try {
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		s.close();
		r.close();
		
		store_to_file_Integer("checkPointsNoDelays", checkpoints);
		
		System.out.println("Echo Finished.\n");
	}
	
	public void image(Scanner ins, int clientPort, int serverPort) {

		// create Datagram Socket s
		DatagramSocket s = null;
		try {
			s = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}

		// find host
		byte[] hostIP = { (byte) 155, (byte) 207, (byte) 18, (byte) 208 };
		InetAddress hostAddress = null;
		try {
			hostAddress = InetAddress.getByAddress(hostIP);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		// create DataGram Socket r
		DatagramSocket r = null;
		try {
			r = new DatagramSocket(clientPort);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		try {
			r.setSoTimeout(10000);
		} catch (SocketException e) {
			e.printStackTrace();
		}

		// insert image_request_code MXXXX
		System.out.print("Please insert int part of image_request_code MXXXX:");
		int imageRequestCode = 2815;
		boolean flag = true;
		do {
			imageRequestCode = ins.nextInt();
			if (imageRequestCode >= 1000 && imageRequestCode <= 9999)
				flag = false;
			else {
				System.out.println("ERROR: please insert 4-digit integer\n");
			}
		} while (flag);

		int buffsize = 1024;
		flag = true;
		System.out.print("Please select buffer size (128, 256, 512, 1024):");
		do {
			buffsize = ins.nextInt();
			if (buffsize == 128 || buffsize == 256 || buffsize == 512 || buffsize == 1024)
				flag = false;
			else {
				System.out.println("ERROR: please insert a valid option\n");
			}
		} while (flag);

		// create txbuffer
		String packetInfo = "M" + Integer.toString(imageRequestCode) + "CAM=FIX" + "FLOW=ON" + "UDP="
				+ Integer.toString(buffsize);
		// System.out.println(packetInfo);
		byte[] txbuffer = packetInfo.getBytes();
		DatagramPacket p = new DatagramPacket(txbuffer, txbuffer.length, hostAddress, serverPort);

		// create rxbuffer
		byte[] rxbuffer = new byte[buffsize];
		DatagramPacket q = new DatagramPacket(rxbuffer, rxbuffer.length);

		// File creation/opening
		FileOutputStream fop = null;
		try {

			File file = new File("imgFIX" + System.currentTimeMillis() + ".jpg");
			if (!file.exists())
				file.createNewFile();
			fop = new FileOutputStream(file);

		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		try {
			s.send(p);
		} catch (IOException e) {

			e.printStackTrace();
		}

		packetInfo = "NEXT";
		txbuffer = packetInfo.getBytes();
		p = new DatagramPacket(txbuffer, txbuffer.length, hostAddress, serverPort);

		// String message;
		for (;;) {

			try {
				r.receive(q);
				// System.out.println("Length: " + q.getLength());
			} catch (Exception x) {
				System.out.println(x);
			}

			// write to file
			try {
				if (fop != null)
					fop.write(rxbuffer, 0, rxbuffer.length);
			} catch (Exception wr) {
				break;
			}

			if (q.getLength() < buffsize)
				break;
			// request next packet
			try {
				s.send(p);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Image 1 received.");

		try {
			fop.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Image 1 saved.");

		// second image
		fop = null;
		try {

			File file = new File("imgPTZ" + System.currentTimeMillis() + ".jpg");
			if (!file.exists())
				file.createNewFile();
			fop = new FileOutputStream(file);

		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		System.out.println("Would you like to move the camera? (Y/n)");
		String cam = "c";
		flag = true;
		do {
			cam = ins.next();
			if (cam.equals("y") || cam.equals("Y") || cam.equals("n") || cam.equals("N"))
				flag = false;
			else {
				System.out.println("ERROR: please insert a valid option\n");
			}
		} while (flag);

		if (cam.equals("N") || cam.equals("n"))
			cam = "CAM=PTZ";
		else {
			System.out.println("Move camera up (U), down (D), left (L), right (R)? : ");
			System.out.println("Memorize position (M), center camera (C)? : ");
			flag = true;
			do {
				cam = ins.next();
				if (cam.equals("U") || cam.equals("D") || cam.equals("L") || cam.equals("R") || cam.equals("M")
						|| cam.equals("C"))
					flag = false;
				else {
					System.out.println("ERROR: please insert a valid option\n");
				}
			} while (flag);
			String pos = cam;
			cam = "CAM=PTZ" + "DIR=" + pos;
		}

		packetInfo = "M" + Integer.toString(imageRequestCode) + cam + "FLOW=ON" + "UDP=" + Integer.toString(buffsize);
		txbuffer = packetInfo.getBytes();
		p = new DatagramPacket(txbuffer, txbuffer.length, hostAddress, serverPort);

		try {
			s.send(p);
		} catch (IOException e) {

			e.printStackTrace();
		}

		packetInfo = "NEXT";
		txbuffer = packetInfo.getBytes();
		p = new DatagramPacket(txbuffer, txbuffer.length, hostAddress, serverPort);

		for (;;) {

			try {
				r.receive(q);
				// System.out.println("Length: " + q.getLength());
			} catch (Exception x) {
				System.out.println(x);
			}

			// write to file
			try {
				if (fop != null)
					fop.write(rxbuffer, 0, rxbuffer.length);
			} catch (Exception wr) {
				break;
			}

			if (q.getLength() < buffsize)
				break;

			// request next packet
			try {
				s.send(p);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Image 2 received.");

		try {
			fop.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Image 2 saved.");

		s.close();
		r.close();
		System.out.println("Image finished.");
	}

	public void obdII(int serverPort) {

		System.out.println("TCP-Based OBD-II Experiment Initializing...");

		// Find Server
		byte[] hostIP = { (byte) 155, (byte) 207, (byte) 18, (byte) 208 };
		InetAddress hostAddress = null;
		try {
			hostAddress = InetAddress.getByAddress(hostIP);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		// create socket
		Socket s = null;
		try {
			s = new Socket(hostAddress, serverPort);
		} catch (IOException e) {
			e.printStackTrace();
		}

		OutputStream out = null;
		try {
			out = s.getOutputStream();
		} catch (IOException e2) {
			e2.printStackTrace();
		}

		InputStream in = null;
		try {
			in = s.getInputStream();
		} catch (IOException e2) {
			e2.printStackTrace();
		}

		String msg = null;
		String Mode = "01";
		String[] PID = { "11", // Throttle Position
				"0F", // Intake Air Temperature
				"0C", // Engine RPM
				"0D", // Vehicle Speed
				"05" // Coolant Temperature
		};

		ArrayList<String> TP = new ArrayList<String>();
		ArrayList<String> IAT = new ArrayList<String>();
		ArrayList<String> ERPM = new ArrayList<String>();
		ArrayList<String> VS = new ArrayList<String>();
		ArrayList<String> CT = new ArrayList<String>();
		String str = "";

		// 5 minute loop init
		long startTime = System.currentTimeMillis(), endTime = 0; // for controlling connection time
		int loopcount = 0;

		do {
			for (int i = 0; i < 5; i++) {

				msg = Mode + " " + PID[i] + "\r";

				try {
					out.write(msg.getBytes());
				} catch (IOException e2) {
					e2.printStackTrace();
				}

				str = "";
				for (;;) {
					try {
						int k = in.read();
						// if its the end of the message
						// exit loop
						if (k == 13)
							break;

						str = str + (char) k;
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}

				// store the measurement to the appropriate list
				switch (i) {
				case 0:
					TP.add(str);
					break;
				case 1:
					IAT.add(str);
					break;
				case 2:
					ERPM.add(str);
					break;
				case 3:
					VS.add(str);
					break;
				case 4:
					CT.add(str);
					break;
				}
			}

			// Just to see program is running fine
			loopcount++;
			if (loopcount % 10 == 0)
				System.out.println("CountLoop:" + loopcount);

			endTime = System.currentTimeMillis();
		} while ((endTime - startTime) / 1000.0 < 5 * 60);

		// close socket
		try {
			s.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		ArrayList<Float> TPflt = new ArrayList<Float>();
		ArrayList<Integer> IATint = new ArrayList<Integer>();
		ArrayList<Float> ERPMflt = new ArrayList<Float>();
		ArrayList<Integer> VSint = new ArrayList<Integer>();
		ArrayList<Integer> CTint = new ArrayList<Integer>();

		String[] parts;
		int xx, yy;

		// Process = TP
		for (int i = 0; i < TP.size(); i++) {
			parts = TP.get(i).split(" ");
			xx = Integer.decode("0x" + parts[2]);
			TPflt.add((float) (xx*100/255));
		}
		store_to_file_Float("TP", TPflt);

		// Process IAT
		for (int i = 0; i < IAT.size(); i++) {
			parts = IAT.get(i).split(" ");
			IATint.add(Integer.decode("0x" + parts[2]) - 40);
		}
		store_to_file_Integer("IAT", IATint);

		// Process ERPM
		for (int i = 0; i < IAT.size(); i++) {
			parts = ERPM.get(i).split(" ");
			xx = Integer.decode("0x" + parts[2]);
			yy = Integer.decode("0x" + parts[3]);
			ERPMflt.add((float) (((256 * xx) + yy) / 4));
		}
		store_to_file_Float("ERPM", ERPMflt);

		// Process VS
		for (int i = 0; i < VS.size(); i++) {
			parts = VS.get(i).split(" ");
			VSint.add(Integer.decode("0x" + parts[2]));
		}
		store_to_file_Integer("VS", VSint);

		// Process CT
		for (int i = 0; i < CT.size(); i++) {
			parts = CT.get(i).split(" ");
			CTint.add(Integer.decode("0x" + parts[2]) - 40);
		}
		store_to_file_Integer("CT", CTint);

		System.out.println("OBD-II Experiment Done.");

	}

	public void ithakicopter(Scanner ins, int serverPort) {

		System.out.println("Initializing Ithakikopter via TCP: ");
		// find host
		byte[] hostIP = { (byte) 155, (byte) 207, (byte) 18, (byte) 208 };
		InetAddress hostAddress = null;
		try {
			hostAddress = InetAddress.getByAddress(hostIP);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		Socket s = null;
		OutputStream out = null;
		InputStream in = null;
		String msg = "", str = "";
		int motor;
		ArrayList<Integer> lmotor = new ArrayList<Integer>();
		ArrayList<Integer> rmotor = new ArrayList<Integer>();
		ArrayList<Integer> alt = new ArrayList<Integer>();
		ArrayList<Float> tmp = new ArrayList<Float>();
		ArrayList<Float> p = new ArrayList<Float>();

		boolean flag = true;
		System.out.println("Please insert motors' speeds (150-200)");
		do {
			motor = ins.nextInt();
			if (motor >= 150 && motor <= 200)
				flag = false;
			else {
				System.out.println("ERROR: please insert correct value\n");
			}
		} while (flag);

		msg = "AUTO FLIGHTLEVEL=000" + " LMOTOR=" + Integer.toString(motor) + " RMOTOR=" + Integer.toString(motor)
				+ " PILOT \r\n";

		long startTime = System.currentTimeMillis(), endTime = 0; // for controlling connection time
		do {

			try {
				s = new Socket(hostAddress, serverPort);
			} catch (IOException e) {
				e.printStackTrace();
			}

			try {
				out = s.getOutputStream();
			} catch (IOException e2) {
				e2.printStackTrace();
			}

			try {
				in = s.getInputStream();
			} catch (IOException e2) {
				e2.printStackTrace();
			}

			try {
				out.write(msg.getBytes());
			} catch (IOException e2) {
				e2.printStackTrace();
			}

			str = "";
			for (;;) {
				try {
					int k = in.read();
					str += (char) k;
					if (k == -1) {
						break;
					}
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}

			str = str.substring(str.length() - 97, str.length() - 4);
			lmotor.add(Integer.parseInt(str.substring(20, 23)));
			rmotor.add(Integer.parseInt(str.substring(31, 34)));
			alt.add(Integer.parseInt(str.substring(46, 47)));
			tmp.add(Float.parseFloat(str.substring(60, 66)));
			p.add(Float.parseFloat(str.substring(76, 83)));

			try {
				s.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			System.out.println("Working...");
			endTime = System.currentTimeMillis();
		} while ((endTime - startTime) / 1000.0 < 180);

		// Store telemetry
		store_to_file_Integer("Lmotor" + Integer.toString(motor), lmotor);
		store_to_file_Integer("Rmotor" + Integer.toString(motor), rmotor);
		store_to_file_Integer("Alt" + Integer.toString(motor), alt);
		store_to_file_Float("Tmp" + Integer.toString(motor), tmp);
		store_to_file_Float("P" + Integer.toString(motor), p);

		System.out.println("Ithakikopter Done.");
	}

	public void DPCM(Scanner ins, int clientPort, int serverPort) {

		System.out.println("Initializing sound w/ DPCM...");
		// create Datagram Socket s
		DatagramSocket s = null;
		try {
			s = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}

		// find host
		byte[] hostIP = { (byte) 155, (byte) 207, (byte) 18, (byte) 208 };
		InetAddress hostAddress = null;
		try {
			hostAddress = InetAddress.getByAddress(hostIP);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		// create DataGram Socket r
		DatagramSocket r = null;
		try {
			r = new DatagramSocket(clientPort);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		try {
			r.setSoTimeout(10000);
		} catch (SocketException e) {
			e.printStackTrace();
		}

		// insert sound_request_code AXXXX
		System.out.print("Please insert int part of sound_request_code ΑXXXX:");
		int soundRequestCode = 8807;
		int packetsNo = 300;
		int packetlength = 128;

		boolean flag = true;
		do {
			soundRequestCode = ins.nextInt();
			if (soundRequestCode >= 1000 && soundRequestCode <= 9999)
				flag = false;
			else {
				System.out.println("ERROR: please insert 4-digit integer\n");
			}
		} while (flag);

		System.out.print("Please insert number of seconds of music to download (1-30):");
		flag = true;
		do {
			packetsNo = ins.nextInt();
			if (packetsNo >= 1 && packetsNo <= 30)
				flag = false;
			else {
				System.out.println("ERROR: please insert correct value\n");
			}
		} while (flag);
		packetsNo = packetsNo * 32;

		System.out.print("Would you like a Tone or a Song? (T/F): ");
		String c = "c";
		flag = true;
		do {
			c = ins.next();
			if (c.equals("T") || c.equals("F"))
				flag = false;
			else {
				System.out.println("ERROR: please insert a valid option\n");
			}
		} while (flag);
		
		// create txbuffer
		String packetInfo = "A" + Integer.toString(soundRequestCode) + c + packetsNo;
		byte[] txbuffer = packetInfo.getBytes();
		DatagramPacket p = new DatagramPacket(txbuffer, txbuffer.length, hostAddress, serverPort);

		// create rxbuffer
		byte[] rxbuffer = new byte[packetlength];
		DatagramPacket q = new DatagramPacket(rxbuffer, rxbuffer.length);

		try {
			s.send(p);
		} catch (IOException e) {

			e.printStackTrace();
		}

		int delta[] = new int[packetsNo * 2 * packetlength];
		int k = 0;
		int b_hi = 0, b_lo = 0;

		// receive and store differences
		for (int i = 0; i < packetsNo; i++) {

			try {
				r.receive(q);
				if (i % 60 == 0)
					System.out.println("Downloading...");

			} catch (Exception x) {
				System.out.println(x);
			}

			for (int j = 0; j < rxbuffer.length; j++) {

				b_lo = rxbuffer[j] & 0xF;
				b_lo -= 8;
				b_hi = (rxbuffer[j] >> 4) & 0xF;
				b_hi -= 8;

				delta[k++] = b_lo;
				delta[k++] = b_hi;

			}

		}

		s.close();
		r.close();

		// extract sound samples
		int samples[] = new int[packetsNo * 2 * packetlength];
		samples[0] = 0;

		for (int i = 1; i < samples.length; i++) {

			samples[i] = samples[i - 1] + delta[i];

			if (samples[i] > 127)
				samples[i] = 127;
			if (samples[i] < -128)
				samples[i] = -128;

		}
		
		// save data
		ArrayList<Integer> Adelta = new ArrayList<Integer>();
		for (int i = 0; i < delta.length; i++) {
			Adelta.add(delta[i]);
		}
		store_to_file_Integer(c + "_delta" + System.currentTimeMillis(), Adelta);

		ArrayList<Integer> Asamples = new ArrayList<Integer>();
		for (int i = 0; i < samples.length; i++) {
			Asamples.add(samples[i]);
		}
		store_to_file_Integer(c + "_samples" + System.currentTimeMillis(), Asamples);
		
		System.out.print("Would you like to play the song? (Y/n): ");
		String song = "c";
		flag = true;
		do {
			song = ins.next();
			if (song.equals("y") || song.equals("Y") || song.equals("n") || song.equals("N"))
				flag = false;
			else {
				System.out.println("ERROR: please insert a valid option\n");
			}
		} while (flag);

		if (song.equals("y") || song.equals("Y"))
			playAudio(samples, 8, false);

		System.out.println("Sound w/ DPCM done.");
	}

	public void AQ_DPCM(Scanner ins, int clientPort, int serverPort) {

		System.out.println("Initializing sound w/ AQ-DPCM...");

		// create Datagram Socket s
		DatagramSocket s = null;
		try {
			s = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}

		// find host
		byte[] hostIP = { (byte) 155, (byte) 207, (byte) 18, (byte) 208 };
		InetAddress hostAddress = null;
		try {
			hostAddress = InetAddress.getByAddress(hostIP);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		// create DataGram Socket r
		DatagramSocket r = null;
		try {
			r = new DatagramSocket(clientPort);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		try {
			r.setSoTimeout(10000);
		} catch (SocketException e) {
			e.printStackTrace();
		}

		// insert sound_request_code AXXXX
		System.out.print("Please insert int part of sound_request_code ΑXXXX:");
		int soundRequestCode = 8807;
		int packetsNo = 300;
		int packetlength = 132;

		boolean flag = true;
		do {
			soundRequestCode = ins.nextInt();
			if (soundRequestCode >= 1000 && soundRequestCode <= 9999)
				flag = false;
			else {
				System.out.println("ERROR: please insert 4-digit integer\n");
			}
		} while (flag);

		System.out.print("Please insert number of seconds of music to download (1-30):");
		flag = true;
		do {
			packetsNo = ins.nextInt();
			if (packetsNo >= 1 && packetsNo <= 30)
				flag = false;
			else {
				System.out.println("ERROR: please insert correct value\n");
			}
		} while (flag);
		packetsNo = packetsNo * 32;
		
		// create txbuffer
		String packetInfo = "A" + Integer.toString(soundRequestCode) + "AQ" + "F" + packetsNo;
		byte[] txbuffer = packetInfo.getBytes();
		DatagramPacket p = new DatagramPacket(txbuffer, txbuffer.length, hostAddress, serverPort);

		// create rxbuffer
		byte[] rxbuffer = new byte[packetlength];
		DatagramPacket q = new DatagramPacket(rxbuffer, rxbuffer.length);

		try {
			s.send(p);
		} catch (IOException e) {

			e.printStackTrace();
		}

		int delta[] = new int[packetsNo * 2 * (packetlength - 4)];
		int k = 0;
		int b_hi = 0, b_lo = 0;
		ArrayList<Integer> mu = new ArrayList<Integer>();
		ArrayList<Integer> beta = new ArrayList<Integer>();

		// receive and store parameters and differences
		for (int i = 0; i < packetsNo; i++) {

			try {
				r.receive(q);
				if (i % 100 == 0)
					System.out.println("Downloading...");

			} catch (Exception x) {
				System.out.println(x);
			}

			mu.add(((rxbuffer[1] << 8) & 0xFFFF) + (rxbuffer[0] & 0xFF));
			beta.add(((rxbuffer[3] << 8) & 0xFFFF) + (rxbuffer[2] & 0xFF));

			for (int j = 4; j < rxbuffer.length; j++) {

				b_lo = rxbuffer[j] & 0xF;
				b_lo -= 8;
				b_hi = (rxbuffer[j] >> 4) & 0xF;
				b_hi -= 8;

				delta[k++] = (b_lo * beta.get(i));
				delta[k++] = (b_hi * beta.get(i));
			}

		}

		s.close();
		r.close();

		// extract sound samples
		int samples[] = new int[delta.length];
		samples[0] = 0;

		for (int i = 1; i < samples.length; i++) {

			samples[i] = samples[i - 1] + delta[i];

			if (samples[i] > 32000)
				samples[i] = 32000;
			if (samples[i] < -32000)
				samples[i] = -32000;
		}

		// save data
		store_to_file_Integer("F" + "_mu" + System.currentTimeMillis(), mu);
		store_to_file_Integer("F" + "_beta" + System.currentTimeMillis(), beta);

		ArrayList<Integer> Adelta = new ArrayList<Integer>();
		for (int i = 0; i < delta.length; i++) {
			Adelta.add(delta[i]);
		}
		store_to_file_Integer("F" + "_delta" + System.currentTimeMillis(), Adelta);

		ArrayList<Integer> Asamples = new ArrayList<Integer>();
		for (int i = 0; i < samples.length; i++) {
			Asamples.add(samples[i]);
		}
		store_to_file_Integer("F" + "_samples" + System.currentTimeMillis(), Asamples);

		
		
		System.out.print("Would you like to play the song? (Y/n): ");
		String song = "c";
		flag = true;
		do {
			song = ins.next();
			if (song.equals("y") || song.equals("Y") || song.equals("n") || song.equals("N"))
				flag = false;
			else {
				System.out.println("ERROR: please insert a valid option\n");
			}
		} while (flag);

		if (song.equals("y") || song.equals("Y"))
			playAudio(samples, 16, true);

		System.out.println("Sound w/ AQ-DPCM done.");
	}

	public void playAudio(int samples[], int Q, boolean AQ) {

		byte[] audioBufferOut = null;

		if (AQ == false) {
			// DPCM

			// copy int values to byte array using casting
			audioBufferOut = new byte[samples.length];
			for (int i = 0; i < audioBufferOut.length; i++) {

				audioBufferOut[i] = (byte) (samples[i] & 0xFF);
			}

		} else {

			// AQ_DPCM
			audioBufferOut = new byte[2 * samples.length];
			for (int i = 0; i < samples.length; i++) {
				audioBufferOut[2 * i] = (byte) (samples[i] & 0xFF);
				audioBufferOut[2 * i + 1] = (byte) ((samples[i] >> 8) & 0xFF);
			}
		}

		// play the song
		AudioFormat linearPCM = new AudioFormat(8000, Q, 1, true, false);
		SourceDataLine lineOut = null;
		try {
			lineOut = AudioSystem.getSourceDataLine(linearPCM);
		} catch (LineUnavailableException e) {
			e.printStackTrace();
		}

		try {
			lineOut.open(linearPCM, audioBufferOut.length);
		} catch (LineUnavailableException e) {
			e.printStackTrace();
		}

		lineOut.start();
		lineOut.write(audioBufferOut, 0, audioBufferOut.length);

		lineOut.stop();
		lineOut.close();

	}

	public void store_to_file_Integer(String filename, ArrayList<Integer> Data) {

		System.out.println("Saving " + filename + " to " + filename + ".txt ...");
		// Files creation/opening
		BufferedWriter bw = null;
		try {

			File file = new File(filename + ".txt");
			if (!file.exists())
				file.createNewFile();
			FileWriter wr = new FileWriter(file);
			bw = new BufferedWriter(wr);

			// write to file
			for (int i = 0; i < Data.size(); i++) {
				bw.write(Integer.toString(Data.get(i)) + "\n");
			}

		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		try {
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Done.");
	}

	public void store_to_file_Float(String filename, ArrayList<Float> Data) {

		System.out.println("Saving " + filename + " to " + filename + ".txt ...");
		// Files creation/opening
		BufferedWriter bw = null;
		try {

			File file = new File(filename + ".txt");
			if (!file.exists())
				file.createNewFile();
			FileWriter wr = new FileWriter(file);
			bw = new BufferedWriter(wr);

			// write to file
			for (int i = 0; i < Data.size(); i++) {
				bw.write(Float.toString(Data.get(i)) + "\n");
			}

		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		try {
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Done.");
	}

}
