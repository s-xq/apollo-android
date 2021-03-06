package com.apollographql.apollo.internal.fetcher;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.IdFieldCacheKeyResolver;
import com.apollographql.apollo.NamedCountDownLatch;
import com.apollographql.apollo.Utils;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.cache.normalized.lru.EvictionPolicy;
import com.apollographql.apollo.cache.normalized.lru.LruNormalizedCacheFactory;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.integration.normalizer.EpisodeHeroNameQuery;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class BaseFetcherTest {

  ApolloClient apolloClient;
  MockWebServer server;
  static final int TIMEOUT_SECONDS = 3;

  @Before public void setUp() {
    server = new MockWebServer();
    OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .writeTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build();

    apolloClient = ApolloClient.builder()
        .serverUrl(server.url("/"))
        .okHttpClient(okHttpClient)
        .normalizedCache(new LruNormalizedCacheFactory(EvictionPolicy.NO_EVICTION), new IdFieldCacheKeyResolver())
        .dispatcher(Utils.immediateExecutor())
        .build();
  }

  @After public void shutdown() throws IOException {
    server.shutdown();
  }

  static class TrackingCallback extends ApolloCall.Callback<EpisodeHeroNameQuery.Data> {
    final List<Response<EpisodeHeroNameQuery.Data>> responseList = new ArrayList<>();
    final NamedCountDownLatch completedOrErrorLatch = new NamedCountDownLatch("completedOrErrorLatch", 1);
    final List<Exception> exceptions = new ArrayList<>();
    volatile boolean completed;


    @Override public void onResponse(@Nonnull Response<EpisodeHeroNameQuery.Data> response) {
      if (completed) throw new IllegalStateException("onCompleted already called Do not reuse tracking callback.");
      responseList.add(response);
    }

    @Override public void onFailure(@Nonnull ApolloException e) {
      if (completed) throw new IllegalStateException("onCompleted already called Do not reuse tracking callback.");
      exceptions.add(e);
      completedOrErrorLatch.countDown();
    }

    @Override public void onStatusEvent(@Nonnull ApolloCall.StatusEvent event) {
      if (event == ApolloCall.StatusEvent.COMPLETED) {
        if (completed) throw new IllegalStateException("onCompleted already called Do not reuse tracking callback.");
        completed = true;
        completedOrErrorLatch.countDown();
      }
    }
  }

  MockResponse mockResponse(String fileName) throws IOException {
    return new MockResponse().setChunkedBody(Utils.readFileToString(getClass(), "/" + fileName), 32);
  }

}
