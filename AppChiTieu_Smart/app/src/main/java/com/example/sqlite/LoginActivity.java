package com.example.sqlite;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

// Import các thư viện Google Sign-In
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnLoginGoogle, btnLoginFacebook;
    private TextView tvRegisterLink;
    private DatabaseHelper dbHelper;

    // Biến toàn cục quản lý Client Google Sign-In thật
    private GoogleSignInClient mGoogleSignInClient;
    private static final int RC_SIGN_IN = 9001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kiểm tra trạng thái đăng nhập trước đó
        SharedPreferences prefs = getSharedPreferences("user_session", MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("is_logged_in", false);
        if (isLoggedIn) {
            navigateToMain();
        }

        setContentView(R.layout.activity_login);
        dbHelper = new DatabaseHelper(this);

        initViews();
        handleEvents();

        // ĐÃ SỬA: Cấu hình và khởi tạo đầy đủ mGoogleSignInClient bằng mã Web Client ID mới
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken("72979761271-vdm1tkf661a8q8d3ohlto1fhf90rs8k8.apps.googleusercontent.com") // <-- Nhớ thay mã Web Application mới tạo của bạn vào đây
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso); // Gán cấu hình vào client để tránh lỗi Null làm crash app
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnLoginGoogle = findViewById(R.id.btnLoginGoogle);
        btnLoginFacebook = findViewById(R.id.btnLoginFacebook);
        tvRegisterLink = findViewById(R.id.tvRegisterLink);
    }

    private void handleEvents() {
        // Đăng nhập tài khoản thường bằng SQLite
        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean isSuccess = dbHelper.checkUserLogin(email, password);
            if (isSuccess) {
                saveLoginSession(email, "normal");
                Toast.makeText(this, "Đăng nhập thành công! 🎉", Toast.LENGTH_SHORT).show();
                navigateToMain();
            } else {
                Toast.makeText(this, "Sai tài khoản hoặc mật khẩu!", Toast.LENGTH_SHORT).show();
            }
        });

        // Chuyển hướng sang màn hình đăng ký (Giữ nguyên bản gốc)
        tvRegisterLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        // ĐỂ CHẠY: Gọi Intent để hiện form danh sách tài khoản Google thật công khai
        btnLoginGoogle.setOnClickListener(v -> {
            if (mGoogleSignInClient != null) {
                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, RC_SIGN_IN);
            }
        });

        // Giả lập Đăng nhập bằng Facebook (Giữ tạm thời, có thể cập nhật SDK thật sau)
        btnLoginFacebook.setOnClickListener(v -> {
            String facebookUser = "GenZ Facebook User";
            dbHelper.registerUser(facebookUser, "oauth_verified", "facebook");
            saveLoginSession(facebookUser, "facebook");
            Toast.makeText(this, "Đăng nhập Facebook thành công! 👍", Toast.LENGTH_SHORT).show();
            navigateToMain();
        });
    }

    // ĐÃ TỐI ƯU: Hàm tiếp nhận kết quả và phòng chống xung đột khóa UNIQUE trong SQLite gây sập ứng dụng
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                // Đăng nhập thành công, lấy thông tin tài khoản Google từ thiết bị
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    String googleEmail = account.getEmail(); // Email thật
                    String displayName = account.getDisplayName(); // Tên hiển thị thật

                    // ĐÃ SỬA: Bọc khối try-catch an toàn bảo vệ SQLite không bị văng ứng dụng khi trùng lặp khóa chính
                    try {
                        dbHelper.registerUser(googleEmail, "oauth_verified", "google");
                    } catch (Exception sqliteException) {
                        // Nếu Email đã tồn tại sẵn trong SQLite từ các phiên đăng nhập trước,
                        // hệ thống tự động bỏ qua dòng này để chạy tiếp, không báo lỗi xung đột nữa.
                    }

                    // Lưu trạng thái đăng nhập hệ thống (Giữ nguyên bản gốc)
                    saveLoginSession(googleEmail, "google");

                    Toast.makeText(this, "Chào mừng " + displayName + "! 🎉", Toast.LENGTH_SHORT).show();
                    navigateToMain();
                }
            } catch (ApiException e) {
                // Trả về thông báo lỗi nếu xác thực thất bại
                Toast.makeText(this, "Đăng nhập Google thất bại! Mã lỗi: " + e.getStatusCode(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void saveLoginSession(String email, String type) {
        SharedPreferences prefs = getSharedPreferences("user_session", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("is_logged_in", true);
        editor.putString("current_user_email", email);
        editor.putString("login_type", type);
        editor.apply();
    }

    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}