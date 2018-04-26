/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.rpc.registry.zk;

import com.alipay.sofa.rpc.client.ProviderInfoAttrs;
import com.alipay.sofa.rpc.common.RpcConstants;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.config.ProviderConfig;
import com.alipay.sofa.rpc.config.RegistryConfig;
import com.alipay.sofa.rpc.config.ServerConfig;
import com.alipay.sofa.rpc.context.RpcRuntimeContext;
import com.alipay.sofa.rpc.test.ActivelyDestroyTest;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

/**
 *
 * @author <a href="mailto:lw111072@antfin.com">LiWei.Liangen</a>
 */
public class WarmUpTest extends ActivelyDestroyTest {

    @Test
    public void testWarmUp() throws InterruptedException, NoSuchFieldException, IllegalAccessException {

        RegistryConfig registryConfig = new RegistryConfig()
            .setProtocol("zookeeper")
            .setAddress("127.0.0.1:2181");

        ServerConfig serverConfig = new ServerConfig()
            .setPort(22222)
            .setProtocol(RpcConstants.PROTOCOL_TYPE_BOLT);
        ProviderConfig<WarmUpService> providerConfig = new ProviderConfig<WarmUpService>()
            .setInterfaceId(WarmUpService.class.getName())
            .setRef(new WarmUpServiceImpl(22222))
            .setServer(serverConfig)
            .setRegistry(registryConfig)
            .setParameter(ProviderInfoAttrs.ATTR_WARMUP_TIME, "2000")
            .setParameter(ProviderInfoAttrs.ATTR_WARMUP_WEIGHT, "100")
            .setWeight(0);

        ServerConfig serverConfig2 = new ServerConfig()
            .setPort(22111)
            .setProtocol(RpcConstants.PROTOCOL_TYPE_BOLT);
        ProviderConfig<WarmUpService> providerConfig2 = new ProviderConfig<WarmUpService>()
            .setInterfaceId(WarmUpService.class.getName())
            .setRef(new WarmUpServiceImpl(22111))
            .setServer(serverConfig2)
            .setRegistry(registryConfig)
            .setWeight(0);

        // Mock start time
        long startTime = System.currentTimeMillis();
        Field field = RpcRuntimeContext.class.getDeclaredField("startTime");
        field.setAccessible(true);
        field.setLong(null, startTime);
        Assert.assertEquals(startTime, RpcRuntimeContext.startTime());

        providerConfig.export();
        providerConfig2.export();

        ConsumerConfig<WarmUpService> consumerConfig = new ConsumerConfig<WarmUpService>()
            .setInterfaceId(WarmUpService.class.getName())
            .setRegistry(registryConfig)
            .setProtocol(RpcConstants.PROTOCOL_TYPE_BOLT);
        WarmUpService warmUpService = consumerConfig.refer();

        // Before the 2000 ms, all the traffic goes to 22222.
        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(22222, warmUpService.getPort());
        }

        long et = System.currentTimeMillis();
        Thread.sleep(2010 - (et - startTime));

        // After 2000 ms, all the traffic goes to 22222 && 22111.
        int cnt = 0;
        for (int i = 0; i < 100; i++) {
            if (warmUpService.getPort() == 22111) {
                cnt++;
            }
        }
        Assert.assertTrue(cnt > 0);
    }
}