package com.project.laundryappui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "laundry_commercial";
    private static final String KEY_CUSTOMERS = "customers";
    private static final String KEY_ORDERS = "orders";
    private static final String KEY_LICENSE_END = "license_end";

    private static final long LICENSE_GRACE_DAYS = 3L;


    private static final String STATUS_MASUK = "Pesanan masuk";
    private static final String STATUS_DICUCI = "Sedang dicuci";
    private static final String STATUS_SELESAI = "Selesai dicuci";
    private static final String STATUS_DIAMBIL = "Sudah diambil";

    private final List<Customer> customers = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();

    private SharedPreferences prefs;
    private TextView tvLicenseStatus;
    private OrderAdapter orderAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        tvLicenseStatus = findViewById(R.id.tvLicenseStatus);
        Button btnMonthly = findViewById(R.id.btnMonthly);
        Button btnYearly = findViewById(R.id.btnYearly);
        Button btnAddOrder = findViewById(R.id.btnAddOrder);
        Button btnScan = findViewById(R.id.btnScan);
        ListView listOrders = findViewById(R.id.listOrders);

        loadData();

        orderAdapter = new OrderAdapter(this, orders);
        listOrders.setAdapter(orderAdapter);

        updateLicenseText();

        btnMonthly.setOnClickListener(v -> activateLicense(30));
        btnYearly.setOnClickListener(v -> activateLicense(365));
        btnAddOrder.setOnClickListener(v -> showAddOrderDialog());
        btnScan.setOnClickListener(v -> startBarcodeScanner());

        listOrders.setOnItemClickListener((parent, view, position, id) -> {
            Order order = orders.get(position);
            if (canWriteData()) {
                moveToNextStatus(order, true);
            } else {
                Toast.makeText(this, "Mode baca saja: tidak bisa update status.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void activateLicense(int days) {
        long now = System.currentTimeMillis();
        long currentEnd = prefs.getLong(KEY_LICENSE_END, 0);
        long start = Math.max(now, currentEnd);
        long newEnd = start + (days * 24L * 60L * 60L * 1000L);
        prefs.edit().putLong(KEY_LICENSE_END, newEnd).apply();
        updateLicenseText();
        Toast.makeText(this, "Lisensi berhasil diaktifkan.", Toast.LENGTH_SHORT).show();
    }

    private boolean isLicenseActive() {
        return System.currentTimeMillis() <= prefs.getLong(KEY_LICENSE_END, 0);
    }

    private boolean isWithinGracePeriod() {
        long end = prefs.getLong(KEY_LICENSE_END, 0);
        long graceEnd = end + (LICENSE_GRACE_DAYS * 24L * 60L * 60L * 1000L);
        return end > 0 && System.currentTimeMillis() > end && System.currentTimeMillis() <= graceEnd;
    }

    private boolean canWriteData() {
        return isLicenseActive();
    }

    private void updateLicenseText() {
        long licenseEnd = prefs.getLong(KEY_LICENSE_END, 0);
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID"));
        if (isLicenseActive()) {
            String endDate = sdf.format(new Date(licenseEnd));
            tvLicenseStatus.setText("Status lisensi: AKTIF sampai " + endDate + " (komersial)");
        } else if (isWithinGracePeriod()) {
            long graceEnd = licenseEnd + (LICENSE_GRACE_DAYS * 24L * 60L * 60L * 1000L);
            tvLicenseStatus.setText("Status lisensi: MASA TENGGANG hingga " + sdf.format(new Date(graceEnd)) + " (mode read-only)");
        } else {
            tvLicenseStatus.setText("Status lisensi: Tidak aktif (read-only). Aktifkan lisensi bulanan/tahunan.");
        }
    }

    private void showAddOrderDialog() {
        if (!canWriteData()) {
            Toast.makeText(this, "Lisensi habis. Aplikasi dalam mode baca saja (read-only).", Toast.LENGTH_LONG).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_order, null);
        CheckBox cbNewCustomer = dialogView.findViewById(R.id.cbNewCustomer);
        Spinner spExistingCustomer = dialogView.findViewById(R.id.spExistingCustomer);
        EditText etName = dialogView.findViewById(R.id.etName);
        EditText etPhone = dialogView.findViewById(R.id.etPhone);
        EditText etAddress = dialogView.findViewById(R.id.etAddress);
        EditText etWeight = dialogView.findViewById(R.id.etWeight);
        EditText etPrice = dialogView.findViewById(R.id.etPrice);

        List<String> customerDisplay = new ArrayList<>();
        for (Customer customer : customers) {
            customerDisplay.add(customer.name + " - " + customer.phone);
        }
        if (customerDisplay.isEmpty()) {
            customerDisplay.add("Belum ada pelanggan, centang pelanggan baru");
            cbNewCustomer.setChecked(true);
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, customerDisplay);
        spExistingCustomer.setAdapter(spinnerAdapter);

        toggleCustomerInput(cbNewCustomer.isChecked(), etName, etPhone, etAddress, spExistingCustomer);
        cbNewCustomer.setOnCheckedChangeListener((buttonView, isChecked) ->
                toggleCustomerInput(isChecked, etName, etPhone, etAddress, spExistingCustomer));

        new AlertDialog.Builder(this)
                .setTitle("Input pesanan baru")
                .setView(dialogView)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (dialog, which) -> {
                    String weightText = etWeight.getText().toString().trim();
                    String priceText = etPrice.getText().toString().trim();

                    if (TextUtils.isEmpty(weightText) || TextUtils.isEmpty(priceText)) {
                        Toast.makeText(this, "Berat dan harga wajib diisi.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Customer selectedCustomer;
                    if (cbNewCustomer.isChecked()) {
                        String name = etName.getText().toString().trim();
                        String phone = etPhone.getText().toString().trim();
                        String address = etAddress.getText().toString().trim();
                        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(phone) || TextUtils.isEmpty(address)) {
                            Toast.makeText(this, "Data pelanggan baru harus lengkap.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        selectedCustomer = new Customer(name, phone, address);
                        customers.add(selectedCustomer);
                    } else {
                        if (customers.isEmpty()) {
                            Toast.makeText(this, "Belum ada pelanggan, silakan tambah pelanggan baru.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        selectedCustomer = customers.get(spExistingCustomer.getSelectedItemPosition());
                    }

                    long parsedPrice;
                    try {
                        parsedPrice = Long.parseLong(priceText);
                    } catch (NumberFormatException ex) {
                        Toast.makeText(this, "Harga harus angka valid.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Order newOrder = new Order();
                    newOrder.code = "LND-" + System.currentTimeMillis();
                    newOrder.customerName = selectedCustomer.name;
                    newOrder.customerPhone = selectedCustomer.phone;
                    newOrder.customerAddress = selectedCustomer.address;
                    newOrder.weightKg = weightText;
                    newOrder.price = parsedPrice;
                    newOrder.status = STATUS_MASUK;
                    newOrder.createdAt = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("id", "ID")).format(new Date());

                    orders.add(0, newOrder);
                    saveData();
                    orderAdapter.notifyDataSetChanged();
                    showReceiptDialog(newOrder);
                })
                .show();
    }

    private void toggleCustomerInput(boolean isNew,
                                     EditText etName,
                                     EditText etPhone,
                                     EditText etAddress,
                                     Spinner spExistingCustomer) {
        etName.setEnabled(isNew);
        etPhone.setEnabled(isNew);
        etAddress.setEnabled(isNew);
        spExistingCustomer.setEnabled(!isNew);
    }

    private void showReceiptDialog(Order order) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_receipt, null);
        ImageView imgBarcode = view.findViewById(R.id.imgBarcode);
        TextView tvReceipt = view.findViewById(R.id.tvReceipt);

        Bitmap barcode = generateBarcode(order.code, 800, 200);
        if (barcode != null) {
            imgBarcode.setImageBitmap(barcode);
        }

        String receiptText = build58mmReceipt(order);
        tvReceipt.setText(receiptText);

        new AlertDialog.Builder(this)
                .setTitle("Pesanan berhasil dibuat")
                .setView(view)
                .setNegativeButton("Tutup", null)
                .setPositiveButton("Cetak Bluetooth", (dialog, which) -> printToBluetooth(order))
                .show();
    }

    private String build58mmReceipt(Order order) {
        NumberFormat formatRupiah = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
        return "=== Laundry Komersial ===\n"
                + "Kode: " + order.code + "\n"
                + "Tanggal: " + order.createdAt + "\n"
                + "Pelanggan: " + order.customerName + "\n"
                + "No HP: " + order.customerPhone + "\n"
                + "Alamat: " + order.customerAddress + "\n"
                + "Berat: " + order.weightKg + " kg\n"
                + "Total: " + formatRupiah.format(order.price) + "\n"
                + "Status: " + order.status + "\n"
                + "=========================";
    }

    private void printToBluetooth(Order order) {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, build58mmReceipt(order));
        startActivity(Intent.createChooser(sendIntent, "Pilih printer Bluetooth POS 58mm"));
    }

    private Bitmap generateBarcode(String text, int width, int height) {
        try {
            BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.CODE_128, width, height);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (WriterException e) {
            return null;
        }
    }

    private void startBarcodeScanner() {
        if (!canWriteData()) {
            Toast.makeText(this, "Lisensi habis. Mode baca saja (read-only).", Toast.LENGTH_SHORT).show();
            return;
        }
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt("Scan barcode pesanan");
        integrator.setBeepEnabled(true);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                updateStatusFromBarcode(result.getContents());
            } else {
                Toast.makeText(this, "Scan dibatalkan.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void updateStatusFromBarcode(String code) {
        for (Order order : orders) {
            if (order.code.equals(code)) {
                if (canWriteData()) {
                moveToNextStatus(order, true);
            } else {
                Toast.makeText(this, "Mode baca saja: tidak bisa update status.", Toast.LENGTH_SHORT).show();
            }
                return;
            }
        }
        Toast.makeText(this, "Kode pesanan tidak ditemukan.", Toast.LENGTH_SHORT).show();
    }

    private void moveToNextStatus(Order order, boolean sendWa) {
        if (STATUS_MASUK.equals(order.status)) {
            order.status = STATUS_DICUCI;
        } else if (STATUS_DICUCI.equals(order.status)) {
            order.status = STATUS_SELESAI;
        } else if (STATUS_SELESAI.equals(order.status)) {
            order.status = STATUS_DIAMBIL;
        } else {
            Toast.makeText(this, "Pesanan sudah selesai sepenuhnya.", Toast.LENGTH_SHORT).show();
            return;
        }

        saveData();
        orderAdapter.notifyDataSetChanged();
        Toast.makeText(this, "Status diperbarui: " + order.status, Toast.LENGTH_SHORT).show();

        if (sendWa) {
            sendWhatsappAsync(order);
        }
    }

    private void sendWhatsappAsync(Order order) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                if (TextUtils.isEmpty(BuildConfig.WA_REMINDER_API_URL) || TextUtils.isEmpty(BuildConfig.WA_REMINDER_API_KEY) || TextUtils.isEmpty(BuildConfig.OWNER_EMAIL)) {
                    runOnUiThread(() -> Toast.makeText(this, "Konfigurasi WA belum diisi di BuildConfig.", Toast.LENGTH_SHORT).show());
                    return;
                }

                URL url = new URL(BuildConfig.WA_REMINDER_API_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("X-API-KEY", BuildConfig.WA_REMINDER_API_KEY);
                connection.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("phone", order.customerPhone);
                payload.put("message", "Halo " + order.customerName + ", status pesanan " + order.code + " sekarang: " + order.status + ".");
                payload.put("owner_email", BuildConfig.OWNER_EMAIL);

                OutputStream outputStream = connection.getOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                writer.write(payload.toString());
                writer.flush();
                writer.close();

                int responseCode = connection.getResponseCode();
                BufferedReader ignored = new BufferedReader(new java.io.InputStreamReader(
                        responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream()));

                runOnUiThread(() -> Toast.makeText(this,
                        responseCode < 400 ? "Notifikasi WhatsApp terkirim." : "Notifikasi WA gagal.",
                        Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Gagal kirim WhatsApp.", Toast.LENGTH_SHORT).show());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void loadData() {
        customers.clear();
        orders.clear();
        try {
            JSONArray customerArray = new JSONArray(prefs.getString(KEY_CUSTOMERS, "[]"));
            for (int i = 0; i < customerArray.length(); i++) {
                JSONObject c = customerArray.getJSONObject(i);
                customers.add(new Customer(c.getString("name"), c.getString("phone"), c.getString("address")));
            }

            JSONArray orderArray = new JSONArray(prefs.getString(KEY_ORDERS, "[]"));
            for (int i = 0; i < orderArray.length(); i++) {
                JSONObject o = orderArray.getJSONObject(i);
                Order order = new Order();
                order.code = o.getString("code");
                order.customerName = o.getString("customerName");
                order.customerPhone = o.getString("customerPhone");
                order.customerAddress = o.getString("customerAddress");
                order.weightKg = o.getString("weightKg");
                order.price = o.getLong("price");
                order.status = o.getString("status");
                order.createdAt = o.getString("createdAt");
                orders.add(order);
            }
        } catch (JSONException ignored) {
        }
    }

    private void saveData() {
        JSONArray customerArray = new JSONArray();
        JSONArray orderArray = new JSONArray();

        try {
            for (Customer customer : customers) {
                JSONObject c = new JSONObject();
                c.put("name", customer.name);
                c.put("phone", customer.phone);
                c.put("address", customer.address);
                customerArray.put(c);
            }

            for (Order order : orders) {
                JSONObject o = new JSONObject();
                o.put("code", order.code);
                o.put("customerName", order.customerName);
                o.put("customerPhone", order.customerPhone);
                o.put("customerAddress", order.customerAddress);
                o.put("weightKg", order.weightKg);
                o.put("price", order.price);
                o.put("status", order.status);
                o.put("createdAt", order.createdAt);
                orderArray.put(o);
            }
        } catch (JSONException ignored) {
        }

        prefs.edit()
                .putString(KEY_CUSTOMERS, customerArray.toString())
                .putString(KEY_ORDERS, orderArray.toString())
                .apply();
    }

    static class Customer {
        final String name;
        final String phone;
        final String address;

        Customer(String name, String phone, String address) {
            this.name = name;
            this.phone = phone;
            this.address = address;
        }
    }

    static class Order {
        String code;
        String customerName;
        String customerPhone;
        String customerAddress;
        String weightKg;
        long price;
        String status;
        String createdAt;
    }

    static class OrderAdapter extends ArrayAdapter<Order> {

        private final NumberFormat formatRupiah = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));

        OrderAdapter(@NonNull Context context, @NonNull List<Order> objects) {
            super(context, 0, objects);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_order_status, parent, false);
            }

            Order order = getItem(position);
            TextView tvCode = convertView.findViewById(R.id.tvCode);
            TextView tvCustomer = convertView.findViewById(R.id.tvCustomer);
            TextView tvPrice = convertView.findViewById(R.id.tvPrice);
            TextView tvStatus = convertView.findViewById(R.id.tvStatus);

            if (order != null) {
                tvCode.setText(order.code + " • " + order.createdAt);
                tvCustomer.setText(order.customerName + " • " + order.customerPhone);
                tvPrice.setText("Total: " + formatRupiah.format(order.price) + " • " + order.weightKg + " kg");
                tvStatus.setText("Status: " + order.status);
            }
            return convertView;
        }
    }
}
