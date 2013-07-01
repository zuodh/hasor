/*
 * Copyright 2008-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moreframework.icache.support;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.more.util.StringUtils;
import org.moreframework.MoreFramework;
import org.moreframework.binder.ApiBinder;
import org.moreframework.context.AppContext;
import org.moreframework.context.PlatformListener;
import org.moreframework.icache.Cache;
import org.moreframework.icache.CacheDefine;
import org.moreframework.icache.CacheManager;
import org.moreframework.icache.DefaultCache;
import org.moreframework.icache.DefaultKeyBuilder;
import org.moreframework.icache.KeyBuilder;
import org.moreframework.icache.KeyBuilderDefine;
import org.moreframework.icache.NeedCache;
import org.moreframework.startup.PlatformExt;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.name.Names;
/**
 * 缓存服务。启动级别：Lv0
 * @version : 2013-4-8
 * @author 赵永春 (zyc@byshell.org)
 */
@PlatformExt(displayName = "CacheModuleServiceListener", description = "org.platform.icache软件包功能支持。", startIndex = PlatformExt.Lv_0)
public class CachePlatformListener implements PlatformListener {
    private CacheManager  cacheManager = null;
    private CacheSettings settings     = null;
    /**初始化.*/
    @Override
    public void initialize(ApiBinder event) {
        //1.挂载Aop
        event.getGuiceBinder().bindInterceptor(new ClassNeedCacheMatcher(), new MethodPowerMatcher(), new CacheInterceptor());
        /*配置文件监听器*/
        this.settings = new CacheSettings();
        this.settings.loadConfig(event.getSettings());
        event.getGuiceBinder().bind(CacheSettings.class).toInstance(this.settings);
        //2.载入缓存配置
        this.loadCache(event);
        this.loadKeyBuilder(event);
        //3.注册Manager
        event.getGuiceBinder().bind(CacheManager.class).to(DefaultCacheManager.class).asEagerSingleton();
    }
    @Override
    public void initialized(AppContext appContext) {
        appContext.getSettings().addSettingsListener(this.settings);
        //
        this.cacheManager = appContext.getInstance(CacheManager.class);
        this.cacheManager.initManager(appContext);
        MoreFramework.info("online ->> cache is %s", (this.settings.isCacheEnable() ? "enable." : "disable."));
    }
    @Override
    public void destroy(AppContext appContext) {
        appContext.getSettings().removeSettingsListener(this.settings);
        this.cacheManager.destroyManager(appContext);
    }
    //
    /*装载KeyBuilder*/
    protected void loadKeyBuilder(ApiBinder event) {
        //1.获取
        Set<Class<?>> iKeyBuilderSet = event.getClassSet(KeyBuilderDefine.class);
        if (iKeyBuilderSet == null)
            return;
        List<Class<? extends KeyBuilder>> iKeyBuilderList = new ArrayList<Class<? extends KeyBuilder>>();
        for (Class<?> cls : iKeyBuilderSet) {
            if (KeyBuilder.class.isAssignableFrom(cls) == false) {
                MoreFramework.warning("loadKeyBuilder : not implemented IKeyBuilder of type %s.", cls);
            } else
                iKeyBuilderList.add((Class<? extends KeyBuilder>) cls);
        }
        //2.排序
        Collections.sort(iKeyBuilderList, new Comparator<Class<?>>() {
            @Override
            public int compare(Class<?> o1, Class<?> o2) {
                KeyBuilderDefine o1Anno = o1.getAnnotation(KeyBuilderDefine.class);
                KeyBuilderDefine o2Anno = o2.getAnnotation(KeyBuilderDefine.class);
                int o1AnnoIndex = o1Anno.sort();
                int o2AnnoIndex = o2Anno.sort();
                return (o1AnnoIndex < o2AnnoIndex ? -1 : (o1AnnoIndex == o2AnnoIndex ? 0 : 1));
            }
        });
        //3.注册服务
        long defaultKeyBuilderIndex = Long.MAX_VALUE;
        Binder binder = event.getGuiceBinder();
        for (Class<? extends KeyBuilder> keyBuildertype : iKeyBuilderList) {
            KeyBuilderDefine keyBuilderAnno = keyBuildertype.getAnnotation(KeyBuilderDefine.class);
            Key<? extends KeyBuilder> keyBuilderKey = Key.get(keyBuildertype);
            KeyBuilderDefinition keyBuilderDefine = new KeyBuilderDefinition(keyBuilderAnno.value(), keyBuilderKey);
            binder.bind(KeyBuilderDefinition.class).annotatedWith(UniqueAnnotations.create()).toInstance(keyBuilderDefine);
            MoreFramework.info("KeyBuilder type:" + MoreFramework.logString(keyBuildertype) + " mapping " + MoreFramework.logString(keyBuilderAnno.value()));
            //确定是否为defaut
            if (keyBuildertype.isAnnotationPresent(DefaultKeyBuilder.class) == true) {
                MoreFramework.warning("KeyBuilder type:" + MoreFramework.logString(keyBuildertype) + " is DefaultKeyBuilder on " + MoreFramework.logString(keyBuilderAnno.value()));
                DefaultKeyBuilder defaultKeyBuilder = keyBuildertype.getAnnotation(DefaultKeyBuilder.class);
                if (defaultKeyBuilder.value() <= defaultKeyBuilderIndex/*数越小越优先*/) {
                    defaultKeyBuilderIndex = defaultKeyBuilder.value();
                    binder.bind(KeyBuilder.class).toProvider(keyBuilderDefine);
                }
            }
        }
    }
    //
    /*装载Cache*/
    protected void loadCache(ApiBinder event) {
        //1.获取
        Set<Class<?>> cacheSet = event.getClassSet(CacheDefine.class);
        if (cacheSet == null)
            return;
        List<Class<Cache<?>>> cacheList = new ArrayList<Class<Cache<?>>>();
        if (cacheSet != null)
            for (Class<?> cls : cacheSet) {
                if (Cache.class.isAssignableFrom(cls) == false) {
                    MoreFramework.warning("loadCache : not implemented ICache of type %s", cls);
                } else
                    cacheList.add((Class<Cache<?>>) cls);
            }
        //3.注册服务
        long defaultCacheIndex = Long.MAX_VALUE;
        Binder binder = event.getGuiceBinder();
        Map<String, Integer> cacheIndex = new HashMap<String, Integer>();
        for (Class<Cache<?>> cacheType : cacheList) {
            CacheDefine cacheAnno = cacheType.getAnnotation(CacheDefine.class);
            for (String cacheName : cacheAnno.value()) {
                MoreFramework.info(cacheName + " at Cache of type " + MoreFramework.logString(cacheType));
                //
                int maxIndex = (cacheIndex.containsKey(cacheName) == false) ? Integer.MAX_VALUE : cacheIndex.get(cacheName);
                if (cacheAnno.sort() <= maxIndex/*数越小越优先*/) {
                    cacheIndex.put(cacheName, cacheAnno.sort());
                    //
                    CacheDefinition cacheDefine = new CacheDefinition(cacheName, cacheType);
                    binder.bind(CacheDefinition.class).annotatedWith(Names.named(cacheName)).toInstance(cacheDefine);
                    binder.bind(Cache.class).annotatedWith(Names.named(cacheName)).toProvider(cacheDefine);
                    //确定是否为defaut
                    if (cacheType.isAnnotationPresent(DefaultCache.class) == true) {
                        MoreFramework.warning(cacheName + " is DefaultCache!");
                        DefaultCache defaultCache = cacheType.getAnnotation(DefaultCache.class);
                        if (defaultCache.value() <= defaultCacheIndex/*数越小越优先*/) {
                            defaultCacheIndex = defaultCache.value();
                            binder.bind(Cache.class).toProvider(cacheDefine);
                        }
                    }
                }
            }
        }
    }
    /*-------------------------------------------------------------------------------------*/
    /*负责检测类是否匹配。规则：只要类型或方法上标记了@NeedCache。*/
    private class ClassNeedCacheMatcher extends AbstractMatcher<Class<?>> {
        @Override
        public boolean matches(Class<?> matcherType) {
            /*如果处于禁用状态则忽略缓存检测*/
            if (settings.isCacheEnable() == false)
                return false;
            /*----------------------------*/
            if (matcherType.isAnnotationPresent(NeedCache.class) == true)
                return true;
            Method[] m1s = matcherType.getMethods();
            Method[] m2s = matcherType.getDeclaredMethods();
            for (Method m1 : m1s) {
                if (m1.isAnnotationPresent(NeedCache.class) == true)
                    return true;
            }
            for (Method m2 : m2s) {
                if (m2.isAnnotationPresent(NeedCache.class) == true)
                    return true;
            }
            return false;
        }
    }
    /*负责检测方法是否匹配。规则：方法或方法所处类上标记了@NeedCache。*/
    private class MethodPowerMatcher extends AbstractMatcher<Method> {
        @Override
        public boolean matches(Method matcherType) {
            /*如果处于禁用状态则忽略缓存检测*/
            if (settings.isCacheEnable() == false)
                return false;
            /*----------------------------*/
            if (matcherType.isAnnotationPresent(NeedCache.class) == true)
                return true;
            if (matcherType.getDeclaringClass().isAnnotationPresent(NeedCache.class) == true)
                return true;
            return false;
        }
    }
    /*拦截器*/
    private class CacheInterceptor implements MethodInterceptor {
        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            /*如果处于禁用状态则忽略缓存检测*/
            if (settings.isCacheEnable() == false)
                return invocation.proceed();
            /*----------------------------*/
            //1.获取缓存数据
            Method targetMethod = invocation.getMethod();
            NeedCache cacheAnno = targetMethod.getAnnotation(NeedCache.class);
            if (cacheAnno == null)
                cacheAnno = targetMethod.getDeclaringClass().getAnnotation(NeedCache.class);
            if (cacheAnno == null)
                return invocation.proceed();
            //2.获取Key
            StringBuilder cacheKey = new StringBuilder(targetMethod.toString());
            Object[] args = invocation.getArguments();
            if (args != null)
                for (Object arg : args) {
                    if (arg == null) {
                        cacheKey.append("NULL");
                        continue;
                    }
                    /*保证arg参数不为空*/
                    KeyBuilder keyBuilder = cacheManager.getKeyBuilder(arg.getClass());
                    cacheKey.append(keyBuilder.serializeKey(arg));
                }
            MoreFramework.debug("MethodInterceptor Method : %s", targetMethod);
            MoreFramework.debug("MethodInterceptor Cache key :%s", cacheKey.toString());
            //3.获取缓存
            Cache<Object> cacheObject = null;
            if (StringUtils.isBlank(cacheAnno.cacheName()) == true)
                cacheObject = cacheManager.getDefaultCache();
            else
                cacheObject = cacheManager.getCache(cacheAnno.cacheName());
            //4.操作缓存
            String key = cacheKey.toString();
            Object returnData = null;
            if (cacheObject.hasCache(key) == true) {
                MoreFramework.debug("the method return data is from Cache.");
                returnData = cacheObject.fromCache(key);
            } else {
                MoreFramework.debug("set data to Cache key :" + key);
                returnData = invocation.proceed();
                cacheObject.toCache(key, returnData, cacheAnno.timeout());
            }
            return returnData;
        }
    }
}