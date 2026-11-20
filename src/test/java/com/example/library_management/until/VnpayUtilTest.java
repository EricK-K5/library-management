package com.example.library_management.until;

import com.example.library_management.util.VnpayUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnpayUtilTest {

    private static final String SECRET = "test-secret";

    // tao 1 bo tham so da ky dung nhu VNPay gui ve
    private static Map<String, String> signedParams() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "REF1");
        params.put("vnp_Amount", "3000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_OrderInfo", "Thanh toan tien phat #3");
        params.put("vnp_SecureHash", VnpayUtil.hmacSHA512(SECRET, VnpayUtil.buildHashData(params)));
        return params;
    }

    @Test
    void hmacSHA512_matchesKnownTestVector() {
        // Vector chuan cua HMAC-SHA512 (key="key") de chac chan thuat toan va dinh dang hex dung
        assertEquals(
                "b42af09057bac1e2d41708e48a902e09b5ff7f12ab428a4fe86653c73dd248fb"
                        + "82f948a549f7b791a5b41915ee4d1ec3935357e4e2317250d0372afa2ebeeb3a",
                VnpayUtil.hmacSHA512("key", "The quick brown fox jumps over the lazy dog"));
    }

    @Test
    void buildHashData_sortsKeysSkipsEmptyAndSignatureFieldsAndUrlEncodesValues() {
        Map<String, String> params = new HashMap<>();
        params.put("b", "2");
        params.put("a", "x y");              // khoang trang -> '+'
        params.put("e", "a&b");              // ky tu dac biet -> %26
        params.put("c", "");                 // rong -> bo
        params.put("d", null);               // null -> bo
        params.put("vnp_SecureHash", "zzz");         // chu ky khong tu ky chinh no
        params.put("vnp_SecureHashType", "HmacSHA512");

        assertEquals("a=x+y&b=2&e=a%26b", VnpayUtil.buildHashData(params));
    }

    @Test
    void verifySignature_acceptsValidSignatureRegardlessOfCaseAndSignatureType() {
        Map<String, String> params = signedParams();
        assertTrue(VnpayUtil.verifySignature(SECRET, params));

        params.put("vnp_SecureHash", params.get("vnp_SecureHash").toUpperCase()); // VNPay co the gui chu hoa
        params.put("vnp_SecureHashType", "HmacSHA512");                           // khong nam trong du lieu ky
        assertTrue(VnpayUtil.verifySignature(SECRET, params));
    }

    @Test
    void verifySignature_rejectsTamperedWrongSecretAndMissingHash() {
//        du lieu bi sua
        Map<String, String> tampered = signedParams();
        tampered.put("vnp_Amount", "100");                       // doi so tien sau khi ky
        assertFalse(VnpayUtil.verifySignature(SECRET, tampered));

//        sai chu ky
        assertFalse(VnpayUtil.verifySignature("other-secret", signedParams()));

//        khong co chu ky
        Map<String, String> noHash = signedParams();
        noHash.remove("vnp_SecureHash");
        assertFalse(VnpayUtil.verifySignature(SECRET, noHash));

//        chu ky rong
        Map<String, String> blankHash = signedParams();
        blankHash.put("vnp_SecureHash", "  ");
        assertFalse(VnpayUtil.verifySignature(SECRET, blankHash));
    }

    @Test
    void getIpAddress_prefersFirstForwardedIpAndFallsBackToIpv4Loopback() {
//        qua proxy
        MockHttpServletRequest proxied = new MockHttpServletRequest();
        proxied.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
//        xoa ip proxy de kiem tra dung ip client coi chinh xac khong
        proxied.setRemoteAddr("10.0.0.1");
        assertEquals("203.0.113.9", VnpayUtil.getIpAddress(proxied));        // IP that cua client (qua ngrok/proxy)

//        khong qua proxy
        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("198.51.100.7");
        assertEquals("198.51.100.7", VnpayUtil.getIpAddress(direct));

//        localhost
        MockHttpServletRequest ipv6 = new MockHttpServletRequest();
        ipv6.setRemoteAddr("0:0:0:0:0:0:0:1");                                // localhost IPv6: VNPay khong nhan
        assertEquals("127.0.0.1", VnpayUtil.getIpAddress(ipv6));
    }
}