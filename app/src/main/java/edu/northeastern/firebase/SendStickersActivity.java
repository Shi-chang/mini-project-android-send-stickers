package edu.northeastern.firebase;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.northeastern.atyourservice.R;
import edu.northeastern.firebase.entity.Sticker;
import edu.northeastern.firebase.entity.User;
import edu.northeastern.firebase.utils.MiscellaneousUtil;

/**
 * The class for Send Stickers Activity.
 *
 * @author Chen Chen
 * @author Lin Han
 * @author ShiChang Ye
 */
public class SendStickersActivity extends AppCompatActivity {
    // Declares fields.
    private static final String WEATHER_ICON_CLEAR = "WEATHER_ICON_CLEAR";
    private static final String WEATHER_ICON_CLOUDS = "WEATHER_ICON_CLOUDS";
    private static final String WEATHER_ICON_RAIN = "WEATHER_ICON_RAIN";
    private static final String WEATHER_ICON_DRIZZLE = "WEATHER_ICON_DRIZZLE";
    private static final String WEATHER_ICON_RAINBOW = "WEATHER_ICON_RAINBOW";
    private static final String WEATHER_ICON_SMOG = "WEATHER_ICON_SMOG";
    private static final String WEATHER_ICON_SNOW = "WEATHER_ICON_SNOW";
    private static final String WEATHER_ICON_BOLT = "WEATHER_ICON_BOLT";
    private static final String SENT_COUNT = "Sent count: ";

    private static String SERVER_KEY;
    private static final int INITIAL_COUNT = 0;

    private TextView userNameTv;
    private Spinner usersSpinner;
    private ImageView image1, image2, image3, image4, image5, image6, image7, image8;
    private TextView textView1, textView2, textView3, textView4, textView5, textView6, textView7, textView8;
    private Button submitBtn;
    private Button sentHistoryBtn;

    private DatabaseReference mDatabase;
    User currentUser;
    private String userName;

    private List<ImageView> imageViewList;
    private List<TextView> textViewList;
    private List<String> imageStringList;

    private Map<String, TextView> imageToTextView;
    private Map<String, Integer> imageToSendCount;
    private Map<View, String> clickedImageMap;

    private DatabaseReference myDB = FirebaseDatabase.getInstance().getReference();
    private ArrayList<String> spinnerList = new ArrayList<>(); //holds all users available to send stickers to
    private ArrayAdapter<String> adapter;

    /**
     * The onCreate method called when the activity is starting.
     *
     * @param savedInstanceState If the activity is being re-initialized after
     *                           previously being shut down then this Bundle contains the data it
     *                           most recently supplied in {@link #onSaveInstanceState}. <b><i>Note:
     *                           Otherwise it is null.</i></b>
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_stickers);

        mDatabase = FirebaseDatabase.getInstance().getReference();

        createNotificationChannel();

        // Binds widgets from the layout to the fields.
        userNameTv = findViewById(R.id.userNameTv);
        usersSpinner = findViewById(R.id.usersSpinner);
        submitBtn = findViewById(R.id.submitBtn);
        sentHistoryBtn = findViewById(R.id.stickersCollectedBtn);

        imageToTextView = new HashMap<>();
        imageToSendCount = new HashMap<>();
        clickedImageMap = new HashMap<>();

        // Gets the current user name from the intent.
        Bundle extras = getIntent().getExtras();
        userName = extras.getString("userName");
        userNameTv.setText("Name: " + userName);

        //get user list for the spinner View
        spinnerShowData();
        usersSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String receiverName = usersSpinner.getSelectedItem().toString();
                if (receiverName.strip().length() == 0) {
                    return;
                }
                updateSendCount(receiverName);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // When clicking sent history, go to sentHistory Activity
        sentHistoryBtn.setOnClickListener(view -> {
            onStickersCollectedButton();
        });

        initializeImageViewsAndTextViews();
        syncData();

        submitBtn.setOnClickListener(view -> {
            String receiverName = usersSpinner.getSelectedItem().toString();
            if (receiverName == null || receiverName.strip().length() == 0) {
                Toast.makeText(SendStickersActivity.this, "Please pick a receiver.", Toast.LENGTH_LONG).show();
                return;
            }
            onSubmitButtonClicked(receiverName);

            for (View v : clickedImageMap.keySet()) {
                ((ImageView) v).setColorFilter(null);
            }
            clickedImageMap.clear();
        });

        // Gets the server key.
        SERVER_KEY = "key=" + MiscellaneousUtil.getProperties(this).getProperty("SERVER_KEY");
    }

    /**
     * Handles the click of stickers collected button.
     */
    private void onStickersCollectedButton() {
        syncData();
        Intent intent = new Intent(SendStickersActivity.this, StickersCollectedHistory.class);
        Bundle bundle = new Bundle();
        bundle.putParcelable("currentUser", currentUser);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    /**
     * Gets a list of users from DB and show in the spinner view.
     */
    private void spinnerShowData() {
        mDatabase.child("users").get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                if (!task.isSuccessful()) {
                    Toast.makeText(SendStickersActivity.this, "Failed to fetch data.", Toast.LENGTH_LONG).show();
                    return;
                }

                DataSnapshot result = task.getResult();
                spinnerList.add("---");
                for (DataSnapshot item : result.getChildren()) {
                    spinnerList.add(item.child("userName").getValue(String.class));
                }

                adapter = new ArrayAdapter<String>(SendStickersActivity.this, android.R.layout.simple_spinner_dropdown_item, spinnerList);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                usersSpinner.setAdapter(adapter);
            }
        });
    }

    private void initializeImageViewsAndTextViews() {
        // Binds images and views to the corresponding fields.
        image1 = findViewById(R.id.weather_icon_clear);
        image2 = findViewById(R.id.weather_icon_clouds);
        image3 = findViewById(R.id.weather_icon_rain);
        image4 = findViewById(R.id.weather_icon_drizzle);
        image5 = findViewById(R.id.weather_icon_rainbow);
        image6 = findViewById(R.id.weather_icon_smog);
        image7 = findViewById(R.id.weather_icon_snow);
        image8 = findViewById(R.id.weather_icon_bolt);

        textView1 = findViewById(R.id.weather_icon_clear_sent_times);
        textView2 = findViewById(R.id.weather_icon_clouds_sent_times);
        textView3 = findViewById(R.id.weather_icon_rain_sent_times);
        textView4 = findViewById(R.id.weather_icon_drizzle_sent_times);
        textView5 = findViewById(R.id.weather_icon_rainbow_sent_times);
        textView6 = findViewById(R.id.weather_icon_smog_sent_times);
        textView7 = findViewById(R.id.weather_icon_snow_sent_times);
        textView8 = findViewById(R.id.weather_icon_bolt_sent_times);

        // Initializes image and view maps.
        imageViewList = Arrays.asList(image1, image2, image3, image4, image5, image6, image7, image8);
        imageStringList = Arrays.asList("WEATHER_ICON_CLEAR", "WEATHER_ICON_CLOUDS", "WEATHER_ICON_RAIN", "WEATHER_ICON_DRIZZLE", "WEATHER_ICON_RAINBOW", "WEATHER_ICON_SMOG", "WEATHER_ICON_SNOW",
                "WEATHER_ICON_BOLT");
        textViewList = Arrays.asList(textView1, textView2, textView3, textView4, textView5, textView6, textView7, textView8);
        for (int i = 0; i < imageViewList.size(); i++) {
            ImageView curImageView = imageViewList.get(i);
            curImageView.setClickable(true);
            curImageView.setOnClickListener(view -> onImageClick(view));

            TextView curTextView = textViewList.get(i);
            String curImageString = imageStringList.get(i);
            imageToTextView.put(curImageString, curTextView);
            imageToSendCount.put(curImageString, INITIAL_COUNT);
            curTextView.setText(SENT_COUNT + imageToSendCount.get(curImageView));
        }
    }

    /**
     * Handles the image click event.
     *
     * @param view the image view that is clicked
     */
    private void onImageClick(View view) {
        // If the clicked view is in the clicked status, restore its status.
        if (clickedImageMap.size() != 0 && clickedImageMap.get(view) != null) {
            ((ImageView) view).setColorFilter(null);
            clickedImageMap.remove(view);
            return;
        }

        // Reference: https://developer.android.com/reference/android/widget/ImageView#setColorFilter(int,%20android.graphics.PorterDuff.Mode)
        ((ImageView) view).setColorFilter(Color.GRAY, PorterDuff.Mode.OVERLAY);
        for (View imageView : clickedImageMap.keySet()) {
            ((ImageView) imageView).setColorFilter(null);
        }

        // Clears the previously clicked view and puts the newly clicked view in the map.
        clickedImageMap.clear();
        switch (view.getId()) {
            case R.id.weather_icon_clear:
                clickedImageMap.put(view, WEATHER_ICON_CLEAR);
                break;
            case R.id.weather_icon_clouds:
                clickedImageMap.put(view, WEATHER_ICON_CLOUDS);
                break;
            case R.id.weather_icon_rain:
                clickedImageMap.put(view, WEATHER_ICON_RAIN);
                break;
            case R.id.weather_icon_drizzle:
                clickedImageMap.put(view, WEATHER_ICON_DRIZZLE);
                break;
            case R.id.weather_icon_rainbow:
                clickedImageMap.put(view, WEATHER_ICON_RAINBOW);
                break;
            case R.id.weather_icon_smog:
                clickedImageMap.put(view, WEATHER_ICON_SMOG);
                break;
            case R.id.weather_icon_snow:
                clickedImageMap.put(view, WEATHER_ICON_SNOW);
                break;
            case R.id.weather_icon_bolt:
                clickedImageMap.put(view, WEATHER_ICON_BOLT);
                break;
        }
    }

    /**
     * Fetches data from database and creates the current user and syncs the fields of the current user.
     */
    private void syncData() {
        // Reference: https://firebase.google.com/docs/database/android/read-and-write#read_once_using_get
        mDatabase.child("users").child(userName).get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                if (!task.isSuccessful()) {
                    Toast.makeText(SendStickersActivity.this, "Registration failed: failed to get token.", Toast.LENGTH_LONG).show();
                    return;
                }

                // Retrieves the info from the response and creates the user accordingly.
                currentUser = task.getResult().getValue(User.class);
            }
        });
    }

    /**
     * Updates the send count for text views.
     */
    private void updateSendCount(String receiverName) {
        imageToSendCount.clear();
        resetSendCount();

        if (currentUser == null || currentUser.getStickersSent() == null || currentUser.getStickersSent().size() == 0) {
            return;
        }

        for (Sticker sticker : currentUser.getStickersSent()) {
            if (sticker.getReceiver().equals(receiverName)) {
                String currentImageString = sticker.getStickerDes();
                imageToSendCount.put(currentImageString, imageToSendCount.getOrDefault(currentImageString, 0) + 1);
            }
        }

        for (String currentImageString : imageToSendCount.keySet()) {
            TextView currentTextView = imageToTextView.get(currentImageString);
            Integer currentCount = imageToSendCount.get(currentImageString);
            currentTextView.setText(SENT_COUNT + currentCount);
        }
    }

    private void resetSendCount() {
        for (String currentImageString : imageToTextView.keySet()) {
            TextView currentTextView = imageToTextView.get(currentImageString);
            currentTextView.setText(SENT_COUNT + INITIAL_COUNT);
        }
    }

    /**
     * Do a payload when send message to other user.
     * <p>
     * Citation: Course Module: Week 8 - Firebase Cloud Messaging & Firebase Realtime Database - Firebase Cloud Messaging Video
     * https://northeastern.instructure.com/courses/136736/pages/firebase-cloud-messaging?module_item_id=8369834
     *
     * @param targetUserToken the target user token
     * @param sticker         the sticker will sent to other user
     */
    private void sendMessageToOtherUser(String targetUserToken, Sticker sticker) {
        // Get notification json file
        JSONObject notification = new JSONObject();
        JSONObject data = new JSONObject();
        JSONObject payload = new JSONObject();

        String notificationTitle = "New Sticker From " + sticker.getSender();
        String notificationBody = sticker.getStickerDes();

        try {
            notification.put("title", notificationTitle);
            notification.put("body", notificationBody);
            data.put("title:", "data:" + notificationTitle);
            data.put("body", "data:" + notificationBody);
            payload.put("to", targetUserToken);
            payload.put("priority", "high");
            payload.put("notification", notification);
            payload.put("data", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Creates a new thread to run the network activity to avoid NetworkOnMainThreadException.
        Thread newThread = new Thread(new Runnable() {
            @Override
            public void run() {
                String resp = fcmHttpConnection(SERVER_KEY, payload);
            }
        });

        newThread.start();
    }

    /**
     * Get connection with Firebase Cloud Messaging
     * <p>
     * Citation: Course Module: Week 8 - Firebase Cloud Messaging & Firebase Realtime Database - Firebase Cloud Messaging Video
     * https://northeastern.instructure.com/courses/136736/pages/firebase-cloud-messaging?module_item_id=8369834
     *
     * @param serverToken the token of the current user
     * @param jsonObject  the payload json file
     * @return the FCM response
     */
    private static String fcmHttpConnection(String serverToken, JSONObject jsonObject) {
        // Loads a payload
        try {
            URL url = new URL("https://fcm.googleapis.com/fcm/send");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", serverToken);
            connection.setDoOutput(true);

            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(jsonObject.toString().getBytes());
            outputStream.close();

            // Read FCM response.
            InputStream inputStream = connection.getInputStream();
            return convertStreamToString(inputStream);
        } catch (IOException e) {
            return "NULL";
        }
    }

    /**
     * Converts the input stream to string.
     * The demo code of this module - FirebaseDemo3.java is referenced.
     *
     * @param inputStream the input stream need to be converted to string
     * @return input stream string
     */
    private static String convertStreamToString(InputStream inputStream) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String len;
            while ((len = bufferedReader.readLine()) != null) {
                stringBuilder.append(len);
            }
            bufferedReader.close();
            return stringBuilder.toString().replace(",", ",\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * Handles the click of the submit button.
     *
     * @param receiverName the receiver name
     */
    private void onSubmitButtonClicked(String receiverName) {
        if (clickedImageMap.size() == 0) {
            Toast.makeText(this, "Please select an image.", Toast.LENGTH_LONG).show();
            return;
        }

        if (usersSpinner.getSelectedItem() == null || usersSpinner.getSelectedItem().toString().equals("---")) {
            Toast.makeText(this, "Please select a receiver.", Toast.LENGTH_LONG).show();
            return;
        }

        if (currentUser == null) {
            return;
        }

        String timeStamp = MiscellaneousUtil.getTimeStamp();
        String clickedImageString = "";
        for (String str : clickedImageMap.values()) {
            clickedImageString = str;
        }

        // Adds the current sticker to the sender and receiver's stickers list.
        Sticker newSticker = new Sticker(currentUser.getUserName(), receiverName, timeStamp, clickedImageString);
        currentUser.getStickersSent().add(newSticker);
        mDatabase.child("users").child(currentUser.getUserName()).setValue(currentUser);

        mDatabase.child("users").child(receiverName).get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                if (!task.isSuccessful()) {
                    Toast.makeText(SendStickersActivity.this, "Failed to send the image.", Toast.LENGTH_LONG).show();
                    return;
                }

                User receiver = task.getResult().getValue(User.class);
                receiver.getStickersReceived().add(newSticker);
                mDatabase.child("users").child(receiverName).setValue(receiver);

                updateSendCount(receiverName);
                Toast.makeText(SendStickersActivity.this, "Image sent successfully.", Toast.LENGTH_LONG).show();

                sendMessageToOtherUser(receiver.getUserToken(), newSticker);
            }
        });
    }

    /**
     * Creates the notification channel.
     * <p>
     * Citation: Android Developers / Documentation / UI Guide / Create and manage notification channels
     * https://developer.android.com/develop/ui/views/notifications/channels
     */
    public void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is not in the Support Library.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            String id = getString(R.string.channel_id);
            int importance = NotificationManager.IMPORTANCE_HIGH;

            // create new channel
            NotificationChannel channel = new NotificationChannel(id, name, importance);

            // Set description.
            channel.setDescription(description);

            // Register the channel with the system. You can't change the importance
            // or other notification behaviors after this.
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}