# 🚀 IS-REST: Base REST Services
![Java](https://img.shields.io/badge/Java-11-blue)
![Maven](https://img.shields.io/badge/Maven-Build%20Tool-orange)
![Version](https://img.shields.io/badge/Stable%20Version-6.0.4-brightgreen)

## 📌 Overview
IS-REST provides base REST services for executing standard CRUD operations on business objects. The URL structure follows a consistent pattern:

```markdown
http://projectUrl/services/objects/businessObjectName/id
```

## 📦 Installation
### Maven Dependency
Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>ch.inser.isejawa</groupId>
    <artifactId>is-rest</artifactId>
    <version>6.0.4</version>
</dependency>
```

## 📝 Configuration
### Creating a "person" object
The URL:

	POST http://localhost:9090/nomDuProjet/services/objects/person

The request body:

	{ "per_firstname": "Zlatan",
		"per_update_user": 0,
		"per_name": "Ibrahimović",
		"per_telnbr": "11122333"
	}	

To view the IS-REST API with all CRUD actions, you can use the Swagger tool. See the Swagger section below.

### web.xml Configuration (RESTEasy 3)
IS-REST is implemented with RESTEasy API. To create a project of type "rest" (instead of "jsf"), you need to configure the web.xml to:
- use the is-rest library servlets
- indicate the relative URL of the application's REST services
- indicate which package contains the REST resources so that the RESTEasy Servlet can scan them.

#### Auto scan REST service

	<!-- Auto scan REST service Resteasy 3 -->
	<context-param>
		<param-name>resteasy.scan</param-name>
		<param-value>true</param-value>
	</context-param>


#### RESTEasy servlet url-pattern

	<context-param>
		<param-name>resteasy.servlet.mapping.prefix</param-name>
		<param-value>/services</param-value>
	</context-param>

#### Standard Servlets

	<servlet>
		<servlet-name>PropertiesServlet</servlet-name>
		<servlet-class>ch.inser.rest.init.PropertiesServlet</servlet-class>
		<init-param>
			<param-name>configDirPropName</param-name>
			<param-value>ch.inser.nomDuProjet.configDir</param-value>
		</init-param>
		<init-param>
			<param-name>configInPackage</param-name>
			<param-value>false</param-value>
		</init-param>
		<init-param>
			<param-name>configFileName</param-name>
			<param-value>nomDuProjet.properties</param-value>
		</init-param>
		<init-param>
			<param-name>contextManagerClass</param-name>
			<param-value>ch.inser.rest.util.GenericContextManager</param-value>
		</init-param>
		<load-on-startup>1</load-on-startup>
	</servlet>
	
	<servlet>
		<servlet-name>Log4JServlet</servlet-name>
		<servlet-class>ch.inser.rest.init.Log4JServlet</servlet-class>
		<init-param>
			<param-name>logDirPropName</param-name>
			<param-value>ch.inser.nomDuProjet.logDir</param-value>
		</init-param>
		<init-param>
			<param-name>configLogFileName</param-name>
			<param-value>log4j.xml</param-value>
		</init-param>
		<load-on-startup>2</load-on-startup>
	</servlet>
	
	<servlet>
		<display-name>Application Initialization Servlet</display-name>
		<servlet-name>appInit</servlet-name>
		<servlet-class>ch.inser.rest.init.AppInitServlet</servlet-class>
		<init-param>
			<param-name>version</param-name>
			<param-value>${project.version}</param-value>
		</init-param>
		<init-param>
			<param-name>build</param-name>
			<param-value>${buildNumber}</param-value>
		</init-param>
		<init-param>
			<param-name>databaseDependenceVersion</param-name>
			<param-value>1</param-value>
		</init-param>
		<load-on-startup>3</load-on-startup>
	</servlet>

### RESTEasy Servlet

	<servlet>
		<servlet-name>RestEasy REST Service</servlet-name>
		<servlet-class>ch.inser.rest.init.RestInitServlet</servlet-class>
		<load-on-startup>4</load-on-startup>
	</servlet>
	<servlet-mapping>
		<servlet-name>RestEasy REST Service</servlet-name>
		<url-pattern>/services/*</url-pattern>
	</servlet-mapping>

### Swagger Servlet

	<servlet>
        <servlet-name>SwaggerServlet</servlet-name>
        <servlet-class>ch.inser.rest.init.SwaggerServlet</servlet-class>
        <load-on-startup>5</load-on-startup>
    </servlet>

## Tomcat web.xml
To allow the frontend in development mode (or Swagger) to communicate with the backend REST services, you need to configure the CORS filter in Tomcat's web.xml.

	<!-- ================== Built In Filter Definitions ===================== -->
	...
	<filter>
	    <filter-name>CorsFilter</filter-name>
	    <filter-class>org.apache.catalina.filters.CorsFilter</filter-class>
	    <init-param>
	        <param-name>cors.allowed.origins</param-name>
	        <param-value>*</param-value>
	    </init-param>
	    <init-param>
	        	<param-name>cors.allowed.headers</param-name>
	        	<param-value>Access-Control-Allow-Origin,token,Content-Type,X-Requested-With,Accept,Accept-Encoding,Accept-Language,Origin,Authorization,Access-Control-Request-Headers,Access-Control-Request-Method,Connection,Host,Referer,Set-Fetch-Dest,Set-Fetch-Site,User-Agent</param-value>
	    </init-param>
	    <init-param>
	        <param-name>cors.allowed.methods</param-name>
	        <param-value>GET,POST,HEAD,OPTIONS,PUT,DELETE,PATCH</param-value>
	    </init-param>
	</filter>
	<filter-mapping>
	    <filter-name>CorsFilter</filter-name>
	    <url-pattern>/*</url-pattern>
	</filter-mapping>

***

## 📜 License
This library is licensed under the **GNU Lesser General Public License v3 (LGPL-3.0)**, as published by the **Free Software Foundation**. You are free to use, modify, and redistribute this library under the terms of the LGPL-3.0 license, either version 3 of the License, or (at your option) any later version.

## 📢 Contact
INSER SA
🔗 **Website**: [www.inser.ch](https://www.inser.ch)
📍 **Address**: INSER SA, Chem. de Maillefer 36, 1052 Le Mont-sur-Lausanne, Vaud, Switzerland

## 👥 Contributing
Contributions are welcome! Please submit pull requests with:
- Clear descriptions
- Unit tests
- Documentation updates
