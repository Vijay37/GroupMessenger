package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
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

import static android.content.ContentValues.TAG;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String[] REMOTE_PORTS = {"11108","11112","11116","11120","11124"};
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final int SERVER_PORT = 10000;
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
        final EditText editText = (EditText) findViewById(R.id.editText1);
        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
                                                          @Override
                                                          public void onClick(View v) {
                                                              String message = editText.getText().toString();
                                                              editText.setText("");
                                                              Log.v("User input",message);
                                                              new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message);
                                                          }
                                                      }
        );
    }
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        private ContentResolver mContentResolver;
        private Uri mUri;
        private OnPTestClickListener Opt;
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            String message;
            Uri providerUri;
            providerUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
            int seq_no = 0; // Sequence number used as a key to store message
            Log.v("Server task","created");
            while(true){
                try {
                    // Source : https://docs.oracle.com/javase/tutorial/networking/sockets/clientServer.html
                    Socket client_sock = serverSocket.accept();
                    Log.v("Message received","\n");
                    new message_store_task().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,client_sock,seq_no,providerUri);
                    seq_no++;
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
        Uri buildUri(String scheme, String authority) {
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority(authority);
            uriBuilder.scheme(scheme);
            return uriBuilder.build();
        }

    }
    private class message_store_task extends  AsyncTask<Object,String,Void>{

        @Override
        protected Void doInBackground(Object... objects) {
            Socket client_sock = (Socket)objects[0];
            int seq_no = (Integer)objects[1];
            Uri providerUri = (Uri)objects[2];
            String message;
            Log.v("Message storing:","started");
            try {
                BufferedReader bR = new BufferedReader(new InputStreamReader(client_sock.getInputStream()));
                message = bR.readLine();
                ContentValues message_carrier = new ContentValues();
                message_carrier.put("key", Integer.toString(seq_no));
                message_carrier.put("value", message);
                getContentResolver().insert(providerUri,
                        message_carrier);
                publishProgress(message);
                client_sock.close();
            }catch(Exception e){
                e.printStackTrace();
            }
            return null;
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
                Socket socket;
                Log.v("Message to send", msgToSend);
                for(int index=0;index<REMOTE_PORTS.length;index++) {
                    Log.v("Send message to",REMOTE_PORTS[index]);
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORTS[index]));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(msgToSend);
                    socket.close();
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
