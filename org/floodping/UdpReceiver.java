package org.floodping;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;
import java.util.TimeZone;

public class UdpReceiver implements Runnable {

	@Override
	public void run() {
		while (true) {
			try {
				internal_run();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public String GetUrl(String sUrl) {

		try {
			URL uUrl;
			uUrl = new URL(sUrl);
			BufferedReader in = new BufferedReader(new InputStreamReader(uUrl.openStream()));

			String inputLine;
			String sRes = "";
			while ((inputLine = in.readLine()) != null) {
				sRes += inputLine;
			}

			in.close();
			return sRes;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public double calc_dewpoint(int h, int t) {
		double H, dew_point;
		H = (Math.log10(h) - 2.0) / 0.4343 + (17.62 * t) / (243.12 + t);
		dew_point = 243.12 * H / (17.62 - H);
		return Math.round(dew_point * 10) / 10;
	}

	private void internal_run() {
		DatagramSocket serverSocket;
		try {
			serverSocket = new DatagramSocket(12345);
		} catch (SocketException e1) {
			e1.printStackTrace();
			System.exit(1);
			return;
		}

		final byte[] receiveData = new byte[1024];

		while (true) {
			final DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			try {
				serverSocket.setSoTimeout(1000);
				serverSocket.receive(receivePacket);
			} catch (SocketTimeoutException e1) {
				continue;
			} catch (SocketException e1) {
				e1.printStackTrace();
				return;
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}

			final DataInputStream dis = new DataInputStream(new ByteArrayInputStream(receiveData));

			try {
				String airid = "XXXX";
				{
					final Formatter f = new Formatter();
					airid = f.format("%c%c%c%c", dis.readByte(), dis.readByte(), dis.readByte(), dis.readByte()).toString();
					f.close();
				}

				final byte type = dis.readByte();
				switch (type) {
				case 'g': {
					final short x1 = (short) ((dis.readByte() & 0xff) | (dis.readByte() & (0xff << 8)));
					final short y1 = (short) ((dis.readByte() & 0xff) | (dis.readByte() & (0xff << 8)));
					final short z1 = (short) ((dis.readByte() & 0xff) | (dis.readByte() & (0xff << 8)));
					dis.readByte();
					final short x2 = (short) ((dis.readByte() & 0xff) | (dis.readByte() & (0xff << 8)));
					final short y2 = (short) ((dis.readByte() & 0xff) | (dis.readByte() & (0xff << 8)));
					final short z2 = (short) ((dis.readByte() & 0xff) | (dis.readByte() & (0xff << 8)));
					if ((x1 != x2) || (y1 != y2) || (z1 != z2)) {
						System.out.println("error: " + x1 + "!=" + x2 + " || " + y1 + "!=" + y2 + " || " + z1 + "!=" + z2 + "");
						break;
					}
					final double xx = (x1 * 1.0) / 4.6;
					final double yx = (y1 * 1.0) / 4.6;
					final double zx = (z1 * 1.0) / 4.6;

					final double x = xx;
					//final int res = x < -9 ? (x < -13 ? 2 : 1) : 0;
					System.out.println("" + airid + " g:" + xx + "," + yx + "," + zx);

					SimpleDateFormat df = new SimpleDateFormat("dd.MM HH:mm:ss");
					df.setTimeZone(TimeZone.getDefault());
					Main.PutValue(airid, new Integer(new Double(x).intValue()).toString(), df.format(new Date()));

					try {
						final String sFile = "/tmp/airid" + airid;
						final FileWriter fstream = new FileWriter(sFile);
						final BufferedWriter out = new BufferedWriter(fstream);
						final Formatter cmdf = new Formatter();
						out.write(cmdf.format(Locale.ENGLISH, "%+3.1f", x).toString());
						out.close();
	          cmdf.close();
					} catch (final Exception e) {
						System.err.println("Error: " + e.getMessage());
					}

				}
					break;
				case 'f': {
					final int rh1 = ((dis.readByte() & 0xff) | (dis.readByte()) << 8);
					final int t1 = ((dis.readByte() & 0xff) | (dis.readByte() & 0xff) << 8);
					final int p1 = ((dis.readByte() & 0xff) | (dis.readByte() & 0xff) << 8);
					dis.readByte();
					final int rh2 = ((dis.readByte() & 0xff) | (dis.readByte()) << 8);
					final int t2 = ((dis.readByte() & 0xff) | (dis.readByte() & 0xff) << 8);
					final int p2 = ((dis.readByte() & 0xff) | (dis.readByte() & 0xff) << 8);
					if ((p1 != p2) || (t1 != t2) || (rh1 != rh2)) {
						System.out.println("error f: " + rh1 + "!=" + rh2 + " || " + t1 + "!=" + t2 + " || " + p1 + "!=" + p2 + "");
						break;
					}
					final double T = (t1 * 1.0) / 10;
					final int P = p1;
					int RH = rh1 + 55;

					if (RH < 0) {
						System.out.println("RH < 0: " + rh1);
						RH = -1;
					}
					if (RH > 100) {
						System.out.println("RH > 100: " + rh1);
						RH = 101;
					}

					System.out.println("" + airid + " F:" + T + "," + P + "," + RH);

					if ((RH >= 0) && (RH <= 100)) {

						double Dew = this.calc_dewpoint(RH, new Double(T).intValue());

						SimpleDateFormat df = new SimpleDateFormat("dd.MM HH:mm:ss");
						df.setTimeZone(TimeZone.getDefault());
						Main.PutValue(airid + "T", new Double(T).toString(), df.format(new Date()));
						Main.PutValue(airid + "D", new Double(Dew).toString(), df.format(new Date()));
						Main.PutValue(airid + "P", new Integer(P).toString(), df.format(new Date()));
						Main.PutValue(airid + "RH", new Integer(RH).toString(), df.format(new Date()));

						try {
							final String sFile = "/tmp/airid" + airid + "T";
							final FileWriter fstream = new FileWriter(sFile);
							final BufferedWriter out = new BufferedWriter(fstream);
							final Formatter cmdf = new Formatter();
							cmdf.close();
							out.write(cmdf.format("%+3.1f", T).toString());
							out.close();
						} catch (final Exception e) {
							System.err.println("Error: " + e.getMessage());
						}
						try {
							final String sFile = "/tmp/airid" + airid + "D";
							final FileWriter fstream = new FileWriter(sFile);
							final BufferedWriter out = new BufferedWriter(fstream);
							final Formatter cmdf = new Formatter();
							out.write(cmdf.format("%+3.1f", Dew).toString());
		          cmdf.close();
							out.close();
						} catch (final Exception e) {
							System.err.println("Error: " + e.getMessage());
						}
						try {
							final String sFile = "/tmp/airid" + airid + "P";
							final FileWriter fstream = new FileWriter(sFile);
							final BufferedWriter out = new BufferedWriter(fstream);
							final Formatter cmdf = new Formatter();
							out.write(cmdf.format("%+4d", P).toString());
			        cmdf.close();
							out.close();
						} catch (final Exception e) {
							System.err.println("Error: " + e.getMessage());
						}
						try {
							final String sFile = "/tmp/airid" + airid + "RH";
							final FileWriter fstream = new FileWriter(sFile);
							final BufferedWriter out = new BufferedWriter(fstream);
							final Formatter cmdf = new Formatter();
							out.write(cmdf.format("%+3d", RH).toString());
							cmdf.close();
							out.close();
						} catch (final Exception e) {
							System.err.println("Error: " + e.getMessage());
						}
					}

				}
					break;
				case 'T': {
					byte x1 = dis.readByte();
					byte x2 = dis.readByte();
					final short temp = (short) (((x2 & 0xff) << 8) | (x1 & 0xff));
					System.out.println("T:" + temp);
					final double t = (temp * 1.0) / 16;
					final Formatter f = new Formatter();
					final String id = f.format("%02x-%02x %02x:%02x:%02x:%02x:%02x:%02x:%02x:%02x", x1, x2, dis.readByte(), dis.readByte(), dis.readByte(), dis.readByte(), dis.readByte(), dis.readByte(), dis.readByte(), dis.readByte()).toString();
          f.close();
					x1 = dis.readByte();
					x2 = dis.readByte();
					final short temp2 = (short) (((x2 & 0xff) << 8) | (x1 & 0xff));
					if (temp != temp2) {
						break;
					}

					System.out.println("" + airid + " T:" + t + "," + id);
					SimpleDateFormat df = new SimpleDateFormat("dd.MM HH:mm:ss");
					df.setTimeZone(TimeZone.getDefault());
					Main.PutValue(airid, new Double(t).toString(), df.format(new Date()));
					try {
						final String sFile = "/tmp/airid" + airid;
						final FileWriter fstream = new FileWriter(sFile);
						final BufferedWriter out = new BufferedWriter(fstream);
						final Formatter cmdf = new Formatter();
						out.write(cmdf.format("%+3.1f", t).toString());
	          cmdf.close();
						out.close();
					} catch (final Exception e) {// Catch exception if
													// any
						System.err.println("Error: " + e.getMessage());
					}
				}
					break;
				case 'M': {
					byte x[] = new byte[10];
					x[0] = dis.readByte();
					x[1] = dis.readByte();
					// x[2] = dis.readByte();
					// x[3] = dis.readByte();
					// x[4] = dis.readByte();
					// x[5] = dis.readByte();

					final int move = ((x[0] == -86) && (x[1] == -86)) ? 1 : 0;
					System.out.println("" + airid + " M:" + move + " " + x[0] + "," + x[1]);
					SimpleDateFormat df = new SimpleDateFormat("dd.MM HH:mm:ss");
					df.setTimeZone(TimeZone.getDefault());
					Main.PutValue(airid, new Integer(move).toString(), df.format(new Date()));
					try {
						final String sFile = "/tmp/airid" + airid;
						final FileWriter fstream = new FileWriter(sFile);
						final BufferedWriter out = new BufferedWriter(fstream);
						final Formatter cmdf = new Formatter();
						out.write(cmdf.format("%+1d", move).toString());
	          cmdf.close();
						out.close();
					} catch (final Exception e) {// Catch exception if
													// any
						System.err.println("Error: " + e.getMessage());
					}
				}
					break;
				default:
					final String sentence = new String(receivePacket.getData());
					System.out.println("RECEIVED: " + sentence);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
