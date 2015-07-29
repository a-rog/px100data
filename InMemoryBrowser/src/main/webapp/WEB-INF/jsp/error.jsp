<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE HTML>
<html>
	<head>
		<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
		<!-- meta http-equiv="X-UA-Compatible" content="IE=8"-->
		<link type="text/css" rel="stylesheet" href="${pageContext.request.contextPath}/resources/css/db-browser.css">
		<title>DB Browser Error</title>
	</head>
	<body>
		<div class="pageHeader">
			<span>DB Browser Error</span>
			<span style="float:right;">
				<a class="pageHeaderLinks" href="${pageContext.request.contextPath}/browse">Data</a>
				&nbsp;&nbsp;
				<a class="pageHeaderLinks" href="${pageContext.request.contextPath}/j_spring_security_logout">Logout</a>
			</span>
		</div>
        <div style="color:red; font-size:16px;">
           <div style="margin:100px auto 100px auto; width:300px;">${error}</div>
        </div>
	</body>
</html>