package com.pinewoodbuilders.requests.ratelimit;

import okhttp3.Interceptor;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class RateLimitInterceptor implements Interceptor {

    // private RateLimiter rateLimiter = RateLimiter.create(3);

    @Override
    public @NotNull Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());

        // 429 is how the api indicates a rate limit error
        if (!response.isSuccessful() && response.code() == 429) {
            System.err.println("Cloudant: "+response.message());

            // wait & retry
            try {
                System.out.println("wait and retry...");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println("Connection interrupted... retrying...");
            }

            response = chain.proceed(chain.request());
        }

        return response;
    }
}
