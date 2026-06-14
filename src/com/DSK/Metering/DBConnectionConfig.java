package com.DSK.Metering;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DBConnectionConfig {
	private static Properties prop = new Properties();
	static {
		try (InputStream input = DBConnectionConfig.class.getClassLoader().getResourceAsStream("db.properties")) {
			if (input == null) {
				System.err.println("db.properties 파일을 찾을 수 없습니다!");
			} else {
				prop.load(input);
				Class.forName(prop.getProperty("db.driver"));
			}
		} catch (IOException e) {
			System.err.println("db.properties 파일 로드 실패");
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			System.err.println("오라클 JDBC 드라이버를 찾을 수 없습니다.");
			e.printStackTrace();
		}
	}

	// 데이터베이스 연결 객체(Connection)를 반환하는 메서드
	public static Connection getConnection() throws SQLException {
		return DriverManager.getConnection(prop.getProperty("db.url"), prop.getProperty("db.username"),
				prop.getProperty("db.password"));
	}
}