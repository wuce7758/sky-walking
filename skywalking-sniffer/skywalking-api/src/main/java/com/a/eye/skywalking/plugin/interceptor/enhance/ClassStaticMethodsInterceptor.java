package com.a.eye.skywalking.plugin.interceptor.enhance;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.plugin.interceptor.loader.InterceptorInstanceLoader;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * The actual byte-buddy's interceptor to intercept class instance methods.
 * In this class, it provide a bridge between byte-buddy and sky-walking plugin.
 *
 * @author wusheng
 */
public class ClassStaticMethodsInterceptor {
    private static ILog logger = LogManager.getLogger(ClassStaticMethodsInterceptor.class);

    /**
     * A class full name, and instanceof {@link StaticMethodsAroundInterceptor}
     * This name should only stay in {@link String}, the real {@link Class} type will trigger classloader failure.
     * If you want to know more, please check on books about Classloader or Classloader appointment mechanism.
     */
    private String staticMethodsAroundInterceptorClassName;

    /**
     * Set the name of {@link ClassStaticMethodsInterceptor#staticMethodsAroundInterceptorClassName}
     * @param staticMethodsAroundInterceptorClassName class full name.
     */
    public ClassStaticMethodsInterceptor(String staticMethodsAroundInterceptorClassName) {
        this.staticMethodsAroundInterceptorClassName = staticMethodsAroundInterceptorClassName;
    }

    /**
     * Intercept the target static method.
     * @param clazz target class
     * @param allArguments all method arguments
     * @param method method description.
     * @param zuper the origin call ref.
     * @return the return value of target static method.
     * @throws Exception only throw exception because of zuper.call()
     *          or unexpected exception in sky-walking ( This is a bug, if anything triggers this condition ).
     */
    @RuntimeType
    public Object intercept(@Origin Class<?> clazz, @AllArguments Object[] allArguments, @Origin Method method, @SuperCall Callable<?> zuper) throws Exception {
        StaticMethodsAroundInterceptor interceptor = InterceptorInstanceLoader
                .load(staticMethodsAroundInterceptorClassName, clazz.getClassLoader());

        StaticMethodInvokeContext interceptorContext = new StaticMethodInvokeContext(clazz,method.getName(), allArguments, method.getParameterTypes());
        MethodInterceptResult result = new MethodInterceptResult();
        try {
            interceptor.beforeMethod(interceptorContext, result);
        } catch (Throwable t) {
            logger.error("class[{}] before static method[{}] intercept failue:{}", new Object[] {clazz, method.getName(), t.getMessage()}, t);
        }


        Object ret = null;
        try {
            if (!result.isContinue()) {
                ret = result._ret();
            }else {
                ret = zuper.call();
            }
        } catch (Throwable t) {
            try {
                interceptor.handleMethodException(t, interceptorContext);
            } catch (Throwable t2) {
                logger.error("class[{}] handle static method[{}] exception failue:{}", new Object[] {clazz, method.getName(), t2.getMessage()}, t2);
            }
            throw t;
        } finally {
            try {
                ret = interceptor.afterMethod(interceptorContext, ret);
            } catch (Throwable t) {
                logger.error("class[{}] after static method[{}] intercept failue:{}", new Object[] {clazz, method.getName(), t.getMessage()}, t);
            }
        }
        return ret;
    }
}
