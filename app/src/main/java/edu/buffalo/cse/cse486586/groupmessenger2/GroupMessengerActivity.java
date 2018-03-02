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
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.ListIterator;

import static android.content.ContentValues.TAG;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String[] REMOTE_PORTS = {"11108","11112"};//,"11116","11120","11124"};
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final int SERVER_PORT = 10000;
    static final String delimiter=",VIJAYAHA,";
    static final float invalid_priority=-1;
    static String myPort="";
    static int seq_no = 0; // Sequence number used as a key to store message
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

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
        public message_queue(String content, int status, float priority, String uniq_id){
            this.content = content;
            this.status = status;
            this.priority = priority;
            this.uniq_id = uniq_id;
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
        private ContentResolver mContentResolver;
        private Uri mUri;
        private OnPTestClickListener Opt;
        private ArrayList<String> unique_id_list = new ArrayList<String>();
        private int deliverable=1;
        private int not_deliverable=0;
        private HashMap<String,String> msg_map = new HashMap<String,String>();
        private HashMap<String,Integer> status_map = new HashMap<String,Integer>();  // 0-NotDeliverable 1-Deliverable
        private HashMap<String,Float> priority_map = new HashMap<String, Float>();
        private ArrayList<message_queue> msg_queue = new ArrayList<message_queue>();
        private int max_number=0;
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            String message;
            String priority="";
            Log.v("Server task","created");
            while(true){
                try {
                    // Source : https://docs.oracle.com/javase/tutorial/networking/sockets/clientServer.html
                    Socket client_sock = serverSocket.accept();
                    Log.v("Message received","\n");
                    BufferedReader bR = new BufferedReader(new InputStreamReader(client_sock.getInputStream()));
                    message = bR.readLine();
                    if(process_msg(message,max_number)) {
                        priority = max_number + "." + myPort;
                        PrintWriter out = new PrintWriter(client_sock.getOutputStream(), true);
                        out.println(priority);
                        max_number++;  // Incrementing max number so that other messages will not use this again
                    }
                } catch (IOException e) {
                    e.printStackTrace();
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
                msg_queue.add(new message_queue(content,not_deliverable,proposal,uni_id));

                unique_id_list.add(uni_id);  // adding unique id to array list
                msg_map.put(uni_id,content); // inserting content with unique id as key
                status_map.put(uni_id,not_deliverable);  // setting status to not deliverable
                priority_map.put(uni_id,invalid_priority);
                return true;
            }
            else if(msg_split_arr.length==3){  // received priority message from sender
                Log.v("Message Processing","Priority Received");
                priority=msg_split_arr[2];
                Log.v("Message Processing","Priority :"+priority);
                priority_float=Float.parseFloat(priority);
                priority_int=(int) priority_float;
                priority_map.put(uni_id,priority_float);  // updating the priority
                status_map.put(uni_id,deliverable); // Changing the status to deliverable
                if(priority_int>max_number)
                    max_number=priority_int;
                for(message_queue mq : msg_queue){
                    if(mq.getUniq_id().equals(uni_id)){
                        mq.setPriority(priority_float);
                        mq.setStatus(deliverable);
                        break;
                    }
                }
                process_queue();
            }
            return false;
        }
        protected void process_queue(){
            Collections.sort(msg_queue);
            for(message_queue mq : msg_queue)
                Log.v("Sorted List",mq.getContent());
            Log.v("Queue Process","Started");
            int status;
            String content;
            String uId;
            float priority;
            boolean process_queue=true;
            ListIterator uL = unique_id_list.listIterator();
            HashMap<Float,String> priority_msg_map = new HashMap<Float,String>();
            ArrayList<Float> priority_list=new ArrayList<Float>();
            while(uL.hasNext()){
                uId = uL.next().toString();
                status = status_map.get(uId);
                if(status==not_deliverable) {  // check if message is not deliverable
                    process_queue=false;
                    break;
                }
                content = msg_map.get(uId);
                priority = priority_map.get(uId);
                priority_list.add(priority);
                priority_msg_map.put(priority,content);
            }
            unique_id_list.clear();
            if(process_queue){
                Collections.sort(priority_list);
                uL = priority_list.listIterator();
                while(uL.hasNext()){
                    priority = Float.parseFloat(uL.next().toString());
                    content = priority_msg_map.get(priority);
                    store_message(content);
                }
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
            try {

                String msgToSend = msgs[0];
                String myPort = msgs[1];
                Socket socket;
                long time= System.currentTimeMillis();
                String unique_id=time+myPort;
                msgToSend=unique_id+delimiter+msgToSend;  // Adding milliseconds,myport as unique id to message separated by a delimiter
                float prev_count=0;
                float curr_count=0;
                String max_count="";
                String recv_priority="";
                Log.v("Message to send", msgToSend);
                for(int index=0;index<REMOTE_PORTS.length;index++) {
                    Log.v("Send message to",REMOTE_PORTS[index]);
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORTS[index]));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(msgToSend);

                    BufferedReader bR = new BufferedReader(new InputStreamReader(socket.getInputStream()));// Receive priority from receiver
                    recv_priority = bR.readLine();
                    curr_count=Float.parseFloat(recv_priority);
                    Log.v("Receiver Priority : ",recv_priority);

                    if(curr_count>prev_count)// Pick the highest priority so far
                        prev_count=curr_count;
                    socket.close();
                    out.close();
                    bR.close();
                }
                msgToSend=unique_id+delimiter+"Priority"+delimiter+prev_count;
                // Sending priorities to receivers
                Log.v("Priority","Sending priorities to receivers");
                for(int index=0;index<REMOTE_PORTS.length;index++) {
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORTS[index]));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(msgToSend);
                    socket.close();
                    out.close();
                }
            } catch (UnknownHostException e) {
                Log.e(TAG,"ClientTask UnknownHostException");
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
