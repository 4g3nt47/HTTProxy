package com.umarabdul.networking.httproxy;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import com.umarabdul.wrapper.socketwrapper.SocketWrapper;
import com.umarabdul.util.argparser.ArgParser;


/**
* A dynamic HTTP proxy in Java for tunneling HTTP connections.
* @author Umar Abdul
* @version 1.0
* Date: 24/Aug/2020
*/

public class HTTProxy implements Runnable{

  private String lhost;
  private int lport;
  private int backlog;
  private int verbosity; 
  private int workers = 0;
  private ArrayList<Socket> sockets;
  private int timeout = 50;
  private int blockSize = 8096;

  /**
  * HTTProxy's constructor.
  * @param lhost Host to listen on.
  * @param lport Port to listen on.
  * @param backlog Server connection backlog.
  * @param verbosity Level of verbose output, from 0 to 2.
  */
  public HTTProxy(String lhost, int lport, int backlog, int verbosity){

    this.lhost = lhost;
    this.lport = lport;
    this.backlog = backlog;
    this.verbosity = verbosity;
    sockets = new ArrayList<Socket>();
  }

  /**
  * Handles a single connection.
  */
  @Override
  public void run(){

    SocketWrapper client = null;
    synchronized(this){
      try{
        client = new SocketWrapper(sockets.remove(0));
      }catch(IOException e){
        if (verbosity > 1)
          System.out.println("[-] TCProxy: Error wrapping socket: " +e.getMessage());
        workers--;
        return;
      }
    }
    // Parse request header.
    try{
      client.getSocket().setSoTimeout(2000);
    }catch(Exception e){}
    byte[] buffer = client.readBytes(blockSize);
    String[] headers = new String(buffer).split("\n");
    String rhost = null;
    int rport = 80;
    for (String line : headers){
      if (line.startsWith("Host: ")){
        line = line.substring(6).trim();
        if (line.contains(":")){
          rport = Integer.valueOf(line.split(":")[1]);
          rhost = line.split(":")[0];
        }else{
          rhost = line;
        }
        break;
      }
    }
    if (rhost == null){
      try{
        client.getSocket().close();
      }catch(IOException e){}
      workers--;
      return;
    }
    // Connect to destination host.
    SocketWrapper server = null;
    try{
      server = new SocketWrapper(rhost, rport, false);
    }catch(IOException e){
      try{
        client.getSocket().close();
      }catch(IOException e1){}
      workers--;
      return;
    }
    String chost = client.getSocket().getRemoteSocketAddress().toString();
    try{
      server.getSocket().setSoTimeout(timeout);
      client.getSocket().setSoTimeout(timeout);
    }catch(Exception e){}
    server.writeBytes(buffer, 0, buffer.length); // send request data previously obtained.
    // Route.
    if (verbosity > 0)
      System.out.println(String.format("[*] Routing:  %s  ==>  %s:%d...", chost, rhost, rport));
    while (true){
      buffer = server.readBytes(blockSize);
      if (buffer == null)
        break;
      if (buffer.length > 0){
        if (verbosity > 1)
          System.out.println(String.format("[*] %s:%d  (%d bytes)  ==>  %s...", rhost, rport, buffer.length, chost));
        client.writeBytes(buffer, 0, buffer.length);
      }
      buffer = client.readBytes(blockSize);
      if (buffer == null)
        break;
      if (buffer.length > 0){
        if (verbosity > 1)
          System.out.println(String.format("[*] %s  (%d bytes)  ==>  %s:%d...", chost, buffer.length, rhost, rport));
        server.writeBytes(buffer, 0, buffer.length);
      }
    }
    try{
      server.getSocket().close();
      client.getSocket().close();
    }catch(IOException e){}
    workers--;
  }

  /**
  * Start the proxy server.
  */
  public void start(){

    ServerSocket server = null;
    System.out.println(String.format("[*] Starting HTTProxy on: %s:%d...", lhost, lport));
    try{
      server = new ServerSocket(lport, backlog, InetAddress.getByName(lhost));
    }catch(Exception e){
      System.out.println("[-] Error starting TCProxy: " + e.getMessage());
      return;
    }
    System.out.println("[+] Server started, listening for connections...");
    Socket client = null;
    Thread t = null;
    while (true){
      try{
        client = server.accept();
        sockets.add(client);
        t = new Thread(this);
        t.start();
      }catch(SocketTimeoutException e1){
        continue;
      }catch(IOException e2){
        e2.printStackTrace();
        try{
          server.close();
        }catch(IOException e3){}
        return;
      }
    }
  }

  /**
  * Launch the proxy from console.
  * @param args Command line arguments.
  */
  public static void main(String[] args){

    String helpPage = "HTTProxy v1.0 - A simple HTTP Proxy  (Author: https://github.com/UmarAbdul01)\n"+
                      "       Usage: httproxy [options]\n"+
                      "     Options:\n"+
                      "             -h|--host       <host>  :  Host to listen on (default: 0.0.0.0)\n"+
                      "             -p|--port       <port>  :  Port to listen on (default: 8080)\n"+
                      "             -b|--backlog    <int>   :  Server connection queue size\n"+
                      "             -v|--verbosity  <int>   :  Level of verbose output\n"+
                      "               |--help               :  Print this help page";
    ArgParser agp = new ArgParser(args);
    agp.setAlias("host", "h");
    agp.setDefault("host", "0.0.0.0");
    agp.setAlias("port", "p");
    agp.setDefault("port", "8080");
    agp.setAlias("backlog", "b");
    agp.setDefault("backlog", "100");
    agp.setAlias("verbosity", "v");
    agp.setDefault("verbosity", "1");
    if (agp.hasArg("--help")){
      System.out.println(helpPage);
      return;
    }
    HTTProxy proxy = new HTTProxy(agp.getString("host"), agp.getInt("port"), agp.getInt("backlog"), agp.getInt("verbosity"));
    proxy.start();
  }

}
