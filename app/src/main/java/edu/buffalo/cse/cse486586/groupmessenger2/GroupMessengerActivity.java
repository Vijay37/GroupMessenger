package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import static android.content.ContentValues.TAG;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static String[] REMOTE_PORTS = {"11108", "11112", "11116", "11120", "11124"};
    static HashMap<String,Integer> port_status= new HashMap<String,Integer>();  // 0 not ok, 1 ok
    static final int SERVER_PORT = 10000;
    static final String delimiter =",VIJAYAHA,";
    static final String abort_id = "ABORT";
    static final String fail_comm_id = "PFAIL";
    static String myPort="";
    static int seq_no = 0; // Sequence number used as a key to store message
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        // Setting status of all ports to ok
        for(int index=0;index<REMOTE_PORTS.length;index++){
            port_status.put(REMOTE_PORTS[index],1);
        }
        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        final EditText editText = (EditText) findViewById(R.id.editText1);
        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
                                                          @Override
                                                          public void onClick(View v) {
                                                              String message = editText.getText().toString();
                                                              editText.setText("");
                                                              Log.v("User input",message);
                                                              new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message,myPort);
                                                          }
                                                      }
        );
    }
    public class message_queue implements Comparable<message_queue>{
        private String content;
        private int status;
        private float priority;
        private String uniq_id;
        private int delivery_status;
        public message_queue(String content, int status, float priority, String uniq_id, int delivery_status){
            this.content = content;
            this.status = status;
            this.priority = priority;
            this.uniq_id = uniq_id;
            this.delivery_status = delivery_status;
        }
        public String getContent() {
            return content;
        }

        public int getStatus() {
            return status;
        }

        public float getPriority() {
            return priority;
        }

        public String getUniq_id() {
            return uniq_id;
        }
        public int getDelivery_status() {
            return delivery_status;
        }

        public void setDelivery_status(int delivery_status) {
            this.delivery_status = delivery_status;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public void setPriority(float priority) {
            this.priority = priority;
        }

        public void setUniq_id(String uniq_id) {
            this.uniq_id = uniq_id;
        }
        @Override
        public int compareTo(message_queue msg_object) {
            return Float.compare(this.priority, msg_object.getPriority());
        }
    }
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        private int deliverable=1;
        private int not_deliverable=0;
        private ArrayList<message_queue> msg_queue = new ArrayList<message_queue>();
        private int max_number=0;
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            String message;
            String priority="";
            Log.v("Server task","created");
            BufferedReader bR=null;
            while(true){
                try {
                    // Source : https://docs.oracle.com/javase/tutorial/networking/sockets/clientServer.html
                    Socket client_sock = serverSocket.accept();
                    Log.v("Message received","\n");
                    bR = new BufferedReader(new InputStreamReader(client_sock.getInputStream()));
                    message = bR.readLine();
                    message=message.trim();
                    if(process_msg(message,max_number)) {
                        priority = max_number + "." + myPort;
                        PrintWriter out = new PrintWriter(client_sock.getOutputStream(), true);
                        out.println(priority);
                        max_number++;  // Incrementing max number so that other messages will not use this again
                    }
                    client_sock.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                finally {
                    if(bR!=null)
                        try {
                            bR.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                }

            }
        }
        protected boolean process_msg(String msg, int proposal){
            Log.v("Message Processing","Started");
            String[] msg_split_arr;
            String uni_id;
            String content;
            String priority;
            int priority_int;
            float priority_float;
            msg_split_arr=msg.split(delimiter);
            uni_id=msg_split_arr[0];
            Log.v("Message Processing","Unique ID :"+uni_id);
            if(msg_split_arr.length==2){  // received message content from sender
                Log.v("Message Processing","Content Received");
                content=msg_split_arr[1];
                Log.v("Message Processing","Content :"+content);
                if(content.equals(abort_id)){
                    for(message_queue mq : msg_queue){
                        if(mq.getUniq_id().equals(uni_id)){
                            mq.setDelivery_status(deliverable);
                            mq.setStatus(deliverable);
                            break;
                        }
                    }
                    return false;
                }
                else {
                    msg_queue.add(new message_queue(content, not_deliverable, proposal, uni_id, not_deliverable));
                    return true;
                }

            }
            else if(msg_split_arr.length==3){  // received priority message from sender
                content = msg_split_arr[1];
                Log.v("Content",content);
                if(content.equals(fail_comm_id)){
                    clear_failed_messages(msg_split_arr[2]);
                }
                else {
                    Log.v("Message Processing", "Priority Received");
                    priority = msg_split_arr[2];
                    Log.v("Message Processing", "Priority :" + priority);
                    priority_float = Float.parseFloat(priority);
                    priority_int = (int) priority_float;
                    if (priority_int > max_number)  // updating maximum number heard so far
                        max_number = priority_int;
                    for (message_queue mq : msg_queue) {
                        if (mq.getUniq_id().equals(uni_id)) {
                            mq.setPriority(priority_float);
                            mq.setStatus(deliverable);
                            break;
                        }
                    }
                }
                process_queue();
            }
            return false;
        }
        protected void clear_failed_messages(String failed_port){
            Log.v("Clearing Failed msgs :",failed_port);
            String uni_id="";
            ArrayList<message_queue> temp_queue = new ArrayList<message_queue>();
            port_status.put(failed_port,not_deliverable);
            for (message_queue mq : msg_queue) {
                uni_id = mq.getUniq_id();
                uni_id = uni_id.substring(uni_id.length()-failed_port.length(),uni_id.length());
                Log.v("Uni ID after substring",uni_id);
                if(uni_id.equals(failed_port)){
                    temp_queue.add(mq);
//                    mq.setDelivery_status(deliverable);
//                    mq.setStatus(deliverable);
                }
            }
            for(message_queue mq : temp_queue){
                msg_queue.remove(mq);
            }
        }
        protected void process_queue(){
            Collections.sort(msg_queue);
            ArrayList<message_queue> temp_queue = new ArrayList<message_queue>();
            for(message_queue mq : msg_queue){
                if(mq.getStatus() == not_deliverable){
                    break;
                }
                else {
                    if(mq.getDelivery_status()==not_deliverable) {
                        store_message(mq.getContent());
                        temp_queue.add(mq);
//                        mq.setDelivery_status(deliverable);
                    }
                }
            }
            for(message_queue mq : temp_queue){
                msg_queue.remove(mq);
            }
        }
        protected void store_message(String content){
            Log.v("Storing message",content);
            Uri providerUri;
            providerUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
            ContentValues message_carrier = new ContentValues();
            message_carrier.put("key", Integer.toString(seq_no));
            message_carrier.put("value", content);
            seq_no++;
            getContentResolver().insert(providerUri,
                    message_carrier);
            // Displaying the message on the screen
            publishProgress(content);

        }
        Uri buildUri(String scheme, String authority) {
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority(authority);
            uriBuilder.scheme(scheme);
            return uriBuilder.build();
        }
        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");
            return;
        }

    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            String msgToSend = msgs[0];
            String myPort = msgs[1];
            Socket socket;
            long time= System.currentTimeMillis();
            String unique_id=time+myPort;
            msgToSend=unique_id+delimiter+msgToSend;  // Adding milliseconds,myport as unique id to message separated by a delimiter
            float prev_count=0;
            float curr_count=0;
            PrintWriter out=null,out1 =null;
            int index = 0;
            String port=null;
            BufferedReader bR=null;
            String recv_priority="";
            try {
                Log.v("Message to send", msgToSend);
                for(index=0;index<REMOTE_PORTS.length;index++){
                    port=REMOTE_PORTS[index];
                    if(port_status.get(port)==0)
                        continue;
                    Log.v("Send message to",port);
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port)),5000);
                    out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(msgToSend);
                    bR = new BufferedReader(new InputStreamReader(socket.getInputStream()));// Receive priority from receiver
                    while ((recv_priority = bR.readLine()) != null) {
                        curr_count = Float.parseFloat(recv_priority);
                        Log.v("Receiver Priority : ", recv_priority);

                        if (curr_count > prev_count)// Pick the highest priority so far
                            prev_count = curr_count;
                    }
                    socket.close();

                }
                msgToSend=unique_id+delimiter+"Priority"+delimiter+prev_count;
                // Sending priorities to receivers
                Log.v("Priority","Sending priorities to receivers");
                for(index=0;index<REMOTE_PORTS.length;index++) {
                    port =  REMOTE_PORTS[index];
                    if(port_status.get(port)==0)
                        continue;
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port)),500);
                    out1 = new PrintWriter(socket.getOutputStream(), true);
                    out1.println(msgToSend);
                    socket.close();
                }
            } catch (UnknownHostException e) {
                Log.e(TAG,"ClientTask UnknownHostException");
            } catch (ConnectException e){
                final int e1 = Log.e(TAG, "Connection failed exception : " + index);
                handle_exception(index,unique_id,prev_count,msgToSend);
            }catch (SocketTimeoutException e){
                Log.e(TAG,"Connection Timeout exception");
                final int e1 = Log.e(TAG, "Connection failed exception : " + index);
                handle_exception(index,unique_id,prev_count,msgToSend);
            }catch (IOException e) {
                e.printStackTrace();
                final int e1 = Log.e(TAG, "Connection failed exception : " + index);
                handle_exception(index,unique_id,prev_count,msgToSend);
            } catch (Exception e){
                e.printStackTrace();
                final int e1 = Log.e(TAG, "Connection failed exception : " + index);
                handle_exception(index,unique_id,prev_count,msgToSend);
            }finally {
                    if (out != null) {
                        out.close();
                    }
                    if (out1 != null)
                        out1.close();
                    if(bR!=null)
                        try {
                            bR.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
            }

            Log.v("Exiting client task","now");
            return null;
        }
        protected float process_priority(Socket socket,float prev_count,int index, String unique_id,String msgToSend){
            BufferedReader bR=null;
            try {
                String recv_priority = "";
                float curr_count=0;
                bR = new BufferedReader(new InputStreamReader(socket.getInputStream()));// Receive priority from receiver
                while ((recv_priority = bR.readLine()) != null) {
                    curr_count = Float.parseFloat(recv_priority);
                    Log.v("Receiver Priority : ", recv_priority);

                    if (curr_count > prev_count)// Pick the highest priority so far
                        prev_count = curr_count;
                }
                return prev_count;
            }
            catch(Exception e){
                e.printStackTrace();
                handle_exception(index, unique_id,prev_count, msgToSend);
            }
            finally{
                if(bR!=null)
                    try {
                        bR.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }
            return prev_count;
        }
        protected void handle_exception(int index, String unique_id,float prev_count, String msgToSend){
            PrintWriter out=null;
            BufferedReader bR=null;
            try {
                String port;
                Socket socket;
                float curr_count=0;

                String recv_priority="";
                Log.e(TAG,"handling exception of "+index);
                String failed_port = REMOTE_PORTS[index];
                port_status.put(REMOTE_PORTS[index],0); // Setting status of the failed port to not ok
                for(int index_1=index+1;index_1<REMOTE_PORTS.length;index_1++){
                    port=REMOTE_PORTS[index_1];
                    if(port_status.get(port)==0)
                        continue;
                    Log.v("Exception","send message to "+port);
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port)),5000);
                    out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(msgToSend);
                    bR = new BufferedReader(new InputStreamReader(socket.getInputStream()));// Receive priority from receiver
                    while ((recv_priority = bR.readLine()) != null) {
                        curr_count = Float.parseFloat(recv_priority);
                        Log.v("Receiver Priority : ", recv_priority);

                        if (curr_count > prev_count)// Pick the highest priority so far
                            prev_count = curr_count;
                    }
                    socket.close();
                }
                Log.v("Exception","maximum count : "+prev_count);
                msgToSend=unique_id+delimiter+"Priority"+delimiter+prev_count;
                // Sending priorities to receivers
                Log.v("Exception","Sending priorities to receivers");
                for(int index_1=0;index_1<REMOTE_PORTS.length;index_1++) {
                    port =  REMOTE_PORTS[index_1];
                    if(port_status.get(port)==0)
                        continue;
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port)),500);
                    out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(msgToSend);
                    socket.close();
                }
                String failure_comm_msg=unique_id+delimiter+fail_comm_id+delimiter+failed_port;
                for(int index_1=0;index_1<REMOTE_PORTS.length;index_1++){
                    port=REMOTE_PORTS[index_1];
                    if(port_status.get(port)==0)
                        continue;
                    Log.v("Send pfail message to",port);
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port)),500);
                    out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(failure_comm_msg);
                    socket.close();
                }
            }catch(Exception e1){
                e1.printStackTrace();
            }
            finally {
                if(out!=null){
                    out.close();
                }
                if(bR!=null){
                    try {
                        bR.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
