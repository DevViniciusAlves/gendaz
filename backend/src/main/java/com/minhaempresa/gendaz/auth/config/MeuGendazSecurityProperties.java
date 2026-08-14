package com.minhaempresa.gendaz.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "security.meu-gendaz")
public class MeuGendazSecurityProperties {
    private final Otp otp = new Otp();
    private final Onboarding onboarding = new Onboarding();
    private final PublicBooking publicBooking = new PublicBooking();
    private final RateLimit rateLimit = new RateLimit();

    public Otp getOtp() { return otp; }
    public Onboarding getOnboarding() { return onboarding; }
    public PublicBooking getPublicBooking() { return publicBooking; }
    public RateLimit getRateLimit() { return rateLimit; }

    public static class Otp {
        private int ttlMinutes = 10;
        private int maxAttempts = 5;
        private int resendCooldownSeconds = 60;
        private int maxRequestsPerEmailHour = 5;
        private int maxRequestsPerIp10m = 20;
        private int maxValidatePerIp10m = 50;
        private int blockMinutes = 30;
        private String secret = "${MEU_GENDAZ_OTP_SECRET:}";

        public Duration ttl() { return Duration.ofMinutes(ttlMinutes); }
        public Duration resendCooldown() { return Duration.ofSeconds(resendCooldownSeconds); }
        public Duration blockDuration() { return Duration.ofMinutes(blockMinutes); }
        public int getTtlMinutes() { return ttlMinutes; }
        public void setTtlMinutes(int ttlMinutes) { this.ttlMinutes = ttlMinutes; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public int getResendCooldownSeconds() { return resendCooldownSeconds; }
        public void setResendCooldownSeconds(int resendCooldownSeconds) { this.resendCooldownSeconds = resendCooldownSeconds; }
        public int getMaxRequestsPerEmailHour() { return maxRequestsPerEmailHour; }
        public void setMaxRequestsPerEmailHour(int maxRequestsPerEmailHour) { this.maxRequestsPerEmailHour = maxRequestsPerEmailHour; }
        public int getMaxRequestsPerIp10m() { return maxRequestsPerIp10m; }
        public void setMaxRequestsPerIp10m(int maxRequestsPerIp10m) { this.maxRequestsPerIp10m = maxRequestsPerIp10m; }
        public int getMaxValidatePerIp10m() { return maxValidatePerIp10m; }
        public void setMaxValidatePerIp10m(int maxValidatePerIp10m) { this.maxValidatePerIp10m = maxValidatePerIp10m; }
        public int getBlockMinutes() { return blockMinutes; }
        public void setBlockMinutes(int blockMinutes) { this.blockMinutes = blockMinutes; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
    }

    public static class Onboarding {
        private int ttlMinutes = 20;
        public Duration ttl() { return Duration.ofMinutes(ttlMinutes); }
        public int getTtlMinutes() { return ttlMinutes; }
        public void setTtlMinutes(int ttlMinutes) { this.ttlMinutes = ttlMinutes; }
    }

    public static class PublicBooking {
        private int maxPostPerIp10m = 10;
        private int maxPostPerPhoneHour = 3;
        private int maxGetPerIpMinute = 60;
        public int getMaxPostPerIp10m() { return maxPostPerIp10m; }
        public void setMaxPostPerIp10m(int maxPostPerIp10m) { this.maxPostPerIp10m = maxPostPerIp10m; }
        public int getMaxPostPerPhoneHour() { return maxPostPerPhoneHour; }
        public void setMaxPostPerPhoneHour(int maxPostPerPhoneHour) { this.maxPostPerPhoneHour = maxPostPerPhoneHour; }
        public int getMaxGetPerIpMinute() { return maxGetPerIpMinute; }
        public void setMaxGetPerIpMinute(int maxGetPerIpMinute) { this.maxGetPerIpMinute = maxGetPerIpMinute; }
    }

    public static class RateLimit {
        private int localMaximumSize = 10_000;
        private int localWindowSeconds = 120;
        private int localDefaultLimit = 60;
        public int getLocalMaximumSize() { return localMaximumSize; }
        public void setLocalMaximumSize(int localMaximumSize) { this.localMaximumSize = localMaximumSize; }
        public int getLocalWindowSeconds() { return localWindowSeconds; }
        public void setLocalWindowSeconds(int localWindowSeconds) { this.localWindowSeconds = localWindowSeconds; }
        public int getLocalDefaultLimit() { return localDefaultLimit; }
        public void setLocalDefaultLimit(int localDefaultLimit) { this.localDefaultLimit = localDefaultLimit; }
    }
}
