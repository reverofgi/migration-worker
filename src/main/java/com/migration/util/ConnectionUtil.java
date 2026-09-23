package com.migration.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Objects;

/** 외부에서 수명 주기를 관리하는 JDBC Connection을 MyBatis에 안전하게 전달한다. */
public final class ConnectionUtil {

    private ConnectionUtil() {
        // 유틸리티 클래스는 인스턴스를 생성하지 않는다.
    }

    /**
     * {@link Connection#close()}만 무시하고 나머지 호출은 원본 Connection에 위임한다.
     *
     * <p> Java Dynamic Proxy와 Reflection을 사용하여
     *  Connection 인터페이스의 모든 호출을 원본 JDBC Connection으로 전달하되,
     *  close()만 가로채서 무시하는 보호용 Connection을 만드는 메서드입니다.
     * 
     * 반환된 Connection으로 생성한 MyBatis SqlSession을 닫아도 원본 Connection은
     * 닫히지 않는다. 원본 Connection은 이를 생성한 Application에서 닫아야 한다.</p>
     *
     * @param connection Application이 소유하는 원본 Connection
     * @return close 호출이 원본에 전달되지 않는 Connection
     */
    public static Connection nonClosing(Connection connection) {
        Connection delegate = Objects.requireNonNull(connection, "connection");
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new NonClosingConnectionHandler(delegate));
    }

    /** Connection 호출을 원본에 위임하되 close 호출만 차단한다. */
    private static final class NonClosingConnectionHandler implements InvocationHandler {
        private final Connection delegate;

        private NonClosingConnectionHandler(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments)
                throws Throwable {
            if (isCloseMethod(method)) {
                return null;
            }
            return invokeDelegate(method, arguments);
        }

        private boolean isCloseMethod(Method method) {
            return "close".equals(method.getName()) && method.getParameterCount() == 0;
        }

        private Object invokeDelegate(
            Method method, Object[] arguments) throws Throwable {
            try {
                return method.invoke(delegate, arguments);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
