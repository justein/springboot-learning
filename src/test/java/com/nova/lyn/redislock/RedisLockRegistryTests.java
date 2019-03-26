package com.nova.lyn.redislock;

import com.nova.lyn.redis.lock.RedisLockRegistry;
import com.nova.lyn.rules.RedisAvailable;
import com.nova.lyn.rules.RedisAvailableTests;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.integration.test.util.TestUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * @ClassName RedisLockRegistryTests
 * @Description TODO
 * @Author Lyn
 * @Date 2019/3/26 0026 下午 4:21
 * @Version 1.0
 */
public class RedisLockRegistryTests extends RedisAvailableTests {

    private final Log logger = LogFactory.getLog(getClass());

    private final String registryKey = UUID.randomUUID().toString();

    private final String registryKey2 = UUID.randomUUID().toString();

    @Before
    @After
    public void setupShutDown() {
        StringRedisTemplate template = this.createTemplate();
        template.delete(this.registryKey + ":*");
        template.delete(this.registryKey2 + ":*");
    }

    private StringRedisTemplate createTemplate() {
        return new StringRedisTemplate(getConnectionFactoryForTest());
    }

    @Test
    @RedisAvailable
    public void testLock() {
        RedisLockRegistry registry = new RedisLockRegistry(getConnectionFactoryForTest(), this.registryKey);
        for (int i = 0; i < 10; i++) {
            Lock lock = registry.obtain("foo");
            lock.lock();
            try {
                assertThat(TestUtils.getPropertyValue(registry, "locks", Map.class).size()).isEqualTo(1);
            }
            finally {
                lock.unlock();
            }
        }
        registry.expireUnusedOlderThan(-1000);
        assertThat(TestUtils.getPropertyValue(registry, "locks", Map.class).size()).isEqualTo(0);
    }

    @Test
    @RedisAvailable
    public void testLockInterruptibly() throws Exception {
        RedisLockRegistry registry = new RedisLockRegistry(getConnectionFactoryForTest(), this.registryKey);
        for (int i = 0; i < 10; i++) {
            Lock lock = registry.obtain("foo");
            lock.lockInterruptibly();
            try {
                assertThat(TestUtils.getPropertyValue(registry, "locks", Map.class).size()).isEqualTo(1);
            }
            finally {
                lock.unlock();
            }
        }
        registry.expireUnusedOlderThan(-1000);
        assertThat(TestUtils.getPropertyValue(registry, "locks", Map.class).size()).isEqualTo(0);
    }

    @Test
    @RedisAvailable
    public void testReentrantLock() {
        RedisLockRegistry registry = new RedisLockRegistry(getConnectionFactoryForTest(), this.registryKey);
        for (int i = 0; i < 10; i++) {
            Lock lock1 = registry.obtain("foo");
            lock1.lock();
            try {
                Lock lock2 = registry.obtain("foo");
                assertThat(lock2).isSameAs(lock1);
                lock2.lock();
                try {
                    // just get the lock
                }
                finally {
                    lock2.unlock();
                }
            }
            finally {
                lock1.unlock();
            }
        }
        registry.expireUnusedOlderThan(-1000);
        assertThat(TestUtils.getPropertyValue(registry, "locks", Map.class).size()).isEqualTo(0);
    }

    @Test
    @RedisAvailable
    public void testReentrantLockInterruptibly() throws Exception {
        RedisLockRegistry registry = new RedisLockRegistry(getConnectionFactoryForTest(), this.registryKey);
        for (int i = 0; i < 10; i++) {
            Lock lock1 = registry.obtain("foo");
            lock1.lockInterruptibly();
            try {
                Lock lock2 = registry.obtain("foo");
                assertThat(lock2).isSameAs(lock1);
                lock2.lockInterruptibly();
                try {
                    // just get the lock
                }
                finally {
                    lock2.unlock();
                }
            }
            finally {
                lock1.unlock();
            }
        }
        registry.expireUnusedOlderThan(-1000);
        assertThat(TestUtils.getPropertyValue(registry, "locks", Map.class).size()).isEqualTo(0);
    }

    @Test
    @RedisAvailable
    public void testTwoLocks() throws Exception {
        RedisLockRegistry registry = new RedisLockRegistry(getConnectionFactoryForTest(), this.registryKey);
        for (int i = 0; i < 10; i++) {
            Lock lock1 = registry.obtain("foo");
            lock1.lockInterruptibly();
            try {
                Lock lock2 = registry.obtain("bar");
                assertThat(lock2).isNotSameAs(lock1);
                lock2.lockInterruptibly();
                try {
                    // just get the lock
                }
                finally {
                    lock2.unlock();
                }
            }
            finally {
                lock1.unlock();
            }
        }
        registry.expireUnusedOlderThan(-1000);
        assertThat(TestUtils.getPropertyValue(registry, "locks", Map.class).size()).isEqualTo(0);
    }

    @Test
    @RedisAvailable
    public void testTwoThreadsSecondFailsToGetLock() throws Exception {
        final RedisLockRegistry registry = new RedisLockRegistry(getConnectionFactoryForTest(), this.registryKey);
        final Lock lock1 = registry.obtain("foo");
        lock1.lockInterruptibly();
        final AtomicBoolean locked = new AtomicBoolean();
        final CountDownLatch latch = new CountDownLatch(1);
        Future<Object> result = Executors.newSingleThreadExecutor().submit(() -> {
            Lock lock2 = registry.obtain("foo");
            locked.set(lock2.tryLock(200, TimeUnit.MILLISECONDS));
            latch.countDown();
            try {
                lock2.unlock();
            }
            catch (IllegalStateException ise) {
                return ise;
            }
            return null;
        });
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(locked.get()).isFalse();
        lock1.unlock();
        Object ise = result.get(10, TimeUnit.SECONDS);
        assertThat(ise).isInstanceOf(IllegalStateException.class);
        assertThat(((Exception) ise).getMessage()).contains("You do not own lock at");
        registry.expireUnusedOlderThan(-1000);
        assertThat(TestUtils.getPropertyValue(registry, "locks", Map.class).size()).isEqualTo(0);
    }

    @Test
    @RedisAvailable
    public void testTwoThreads() throws Exception {
        RedisLockRegistry registry = new RedisLockRegistry(getConnectionFactoryForTest(), this.registryKey);
        Lock lock1 = registry.obtain("foo");
        AtomicBoolean locked = new AtomicBoolean();
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);
        lock1.lockInterruptibly();
        assertThat(TestUtils.getPropertyValue(registry, "locks", Map.class).size()).isEqualTo(1);
        Executors.newSingleThreadExecutor().execute(() -> {
            Lock lock2 = registry.obtain("foo");
            try {
                latch1.countDown();
                lock2.lockInterruptibly();
                assertThat(TestUtils.getPropertyValue(registry, "locks", Map.class).size()).isEqualTo(1);
                latch2.await(10, TimeUnit.SECONDS);
                locked.set(true);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finally {
                lock2.unlock();
                latch3.countDown();
            }
        });
        assertThat(latch1.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(locked.get()).isFalse();
        lock1.unlock();
        latch2.countDown();
        assertThat(latch3.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(locked.get()).isTrue();
        registry.expireUnusedOlderThan(-1000);
        assertThat(TestUtils.getPropertyValue(registry, "locks", Map.class).size()).isEqualTo(0);
    }

    @Test
    @RedisAvailable
    public void testTwoThreadsDifferentRegistries() throws Exception {
        RedisLockRegistry registry1 = new RedisLockRegistry(getConnectionFactoryForTest(), this.registryKey);
        RedisLockRegistry registry2 = new RedisLockRegistry(getConnectionFactoryForTest(), this.registryKey);
        Lock lock1 = registry1.obtain("foo");
        AtomicBoolean locked = new AtomicBoolean();
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);
        lock1.lockInterruptibly();
        assertThat(TestUtils.getPropertyValue(registry1, "locks", Map.class).size()).isEqualTo(1);
        Executors.newSingleThreadExecutor().execute(() -> {
            Lock lock2 = registry2.obtain("foo");
            try {
                latch1.countDown();
                lock2.lockInterruptibly();
                assertThat(TestUtils.getPropertyValue(registry2, "locks", Map.class).size()).isEqualTo(1);
                latch2.await(10, TimeUnit.SECONDS);
                locked.set(true);
            }
            catch (InterruptedException e1) {
                Thread.currentThread().interrupt();
                this.logger.error("Interrupted while locking: " + lock2, e1);
            }
            finally {
                try {
                    lock2.unlock();
                    latch3.countDown();
                }
                catch (IllegalStateException e2) {
                    this.logger.error("Failed to unlock: " + lock2, e2);
                }
            }
        });
        assertThat(latch1.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(locked.get()).isFalse();
        lock1.unlock();
        latch2.countDown();
        assertThat(latch3.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(locked.get()).isTrue();
        registry1.expireUnusedOlderThan(-1000);
        registry2.expireUnusedOlderThan(-1000);
        assertThat(TestUtils.getPropertyValue(registry1, "locks", Map.class).size()).isEqualTo(0);
        assertThat(TestUtils.getPropertyValue(registry2, "locks", Map.class).size()).isEqualTo(0);
    }

    @Test
    @RedisAvailable
    public void testTwoThreadsWrongOneUnlocks() throws Exception {
        RedisLockRegistry registry = new RedisLockRegistry(getConnectionFactoryForTest(), this.registryKey);
        Lock lock = registry.obtain("foo");
        lock.lockInterruptibly();
        AtomicBoolean locked = new AtomicBoolean();
        CountDownLatch latch = new CountDownLatch(1);
        Future<Object> result = Executors.newSingleThreadExecutor().submit(() -> {
            try {
                lock.unlock();
            }
            catch (IllegalStateException ise) {
                latch.countDown();
                return ise;
            }
            return null;
        });
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(locked.get()).isFalse();
        lock.unlock();
        Object ise = result.get(10, TimeUnit.SECONDS);
        assertThat(ise).isInstanceOf(IllegalStateException.class);
        assertThat(((Exception) ise).getMessage()).contains("You do not own lock at");
        registry.expireUnusedOlderThan(-1000);
        assertThat(TestUtils.getPropertyValue(registry, "locks", Map.class).size()).isEqualTo(0);
    }

    @Test
    @RedisAvailable
    public void testExpireTwoRegistries() throws Exception {
        RedisLockRegistry registry1 = new RedisLockRegistry(getConnectionFactoryForTest(), this.registryKey, 100);
        RedisLockRegistry registry2 = new RedisLockRegistry(getConnectionFactoryForTest(), this.registryKey, 100);
        Lock lock1 = registry1.obtain("foo");
        Lock lock2 = registry2.obtain("foo");
        assertThat(lock1.tryLock()).isTrue();
        assertThat(lock2.tryLock()).isFalse();
        waitForExpire("foo");
        assertThat(lock2.tryLock()).isTrue();
        assertThat(lock1.tryLock()).isFalse();
    }

    @Test
    @RedisAvailable
    public void testExceptionOnExpire() throws Exception {
        RedisLockRegistry registry = new RedisLockRegistry(getConnectionFactoryForTest(), this.registryKey, 1);
        Lock lock1 = registry.obtain("foo");
        assertThat(lock1.tryLock()).isTrue();
        waitForExpire("foo");
        assertThatIllegalStateException()
                .isThrownBy(lock1::unlock)
                .withMessageContaining("Lock was released in the store due to expiration.");
    }


    @Test
    @RedisAvailable
    public void testEquals() {
        RedisConnectionFactory connectionFactory = getConnectionFactoryForTest();
        RedisLockRegistry registry1 = new RedisLockRegistry(connectionFactory, this.registryKey);
        RedisLockRegistry registry2 = new RedisLockRegistry(connectionFactory, this.registryKey);
        RedisLockRegistry registry3 = new RedisLockRegistry(connectionFactory, this.registryKey2);
        Lock lock1 = registry1.obtain("foo");
        Lock lock2 = registry1.obtain("foo");
        assertThat(lock2).isEqualTo(lock1);
        lock1.lock();
        lock2.lock();
        assertThat(lock2).isEqualTo(lock1);
        lock1.unlock();
        lock2.unlock();
        assertThat(lock2).isEqualTo(lock1);

        lock1 = registry1.obtain("foo");
        lock2 = registry2.obtain("foo");
        assertThat(lock2).isNotEqualTo(lock1);
        lock1.lock();
        assertThat(lock2.tryLock()).isFalse();
        lock1.unlock();

        lock1 = registry1.obtain("foo");
        lock2 = registry3.obtain("foo");
        assertThat(lock2).isNotEqualTo(lock1);
        lock1.lock();
        lock2.lock();
        lock1.unlock();
        lock2.unlock();
    }

    @Test
    @RedisAvailable
    public void testThreadLocalListLeaks() {
        RedisLockRegistry registry = new RedisLockRegistry(getConnectionFactoryForTest(), this.registryKey, 100);

        for (int i = 0; i < 10; i++) {
            registry.obtain("foo" + i);
        }
        assertThat(TestUtils.getPropertyValue(registry, "locks", Map.class).size()).isEqualTo(10);

        for (int i = 0; i < 10; i++) {
            Lock lock = registry.obtain("foo" + i);
            lock.lock();
        }
        assertThat(TestUtils.getPropertyValue(registry, "locks", Map.class).size()).isEqualTo(10);

        for (int i = 0; i < 10; i++) {
            Lock lock = registry.obtain("foo" + i);
            lock.unlock();
        }
        assertThat(TestUtils.getPropertyValue(registry, "locks", Map.class).size()).isEqualTo(10);
    }

    @Test
    @RedisAvailable
    public void testExpireNotChanged() throws Exception {
        RedisConnectionFactory connectionFactory = getConnectionFactoryForTest();
        final RedisLockRegistry registry = new RedisLockRegistry(connectionFactory, this.registryKey, 10000);
        Lock lock = registry.obtain("foo");
        lock.lock();

        Long expire = getExpire(registry, "foo");

        Future<Object> result = Executors.newSingleThreadExecutor().submit(() -> {
            Lock lock2 = registry.obtain("foo");
            assertThat(lock2.tryLock()).isFalse();
            return null;
        });
        result.get();
        assertThat(getExpire(registry, "foo")).isEqualTo(expire);
        lock.unlock();
    }

    private Long getExpire(RedisLockRegistry registry, String lockKey) {
        StringRedisTemplate template = createTemplate();
        String registryKey = TestUtils.getPropertyValue(registry, "registryKey", String.class);
        return template.getExpire(registryKey + ":" + lockKey);
    }

    private void waitForExpire(String key) throws Exception {
        StringRedisTemplate template = createTemplate();
        int n = 0;
        while (n++ < 100 && template.keys(this.registryKey + ":" + key).size() > 0) {
            Thread.sleep(100);
        }
        assertThat(n < 100).as(key + " key did not expire").isTrue();
    }
}
