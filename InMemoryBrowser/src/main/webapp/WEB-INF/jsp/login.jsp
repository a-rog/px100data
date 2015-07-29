<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE HTML>
<html>
	<head>
		<meta http-equiv="content-type" content="text/html; charset=UTF-8">
		<link type="text/css" rel="stylesheet" href="${pageContext.request.contextPath}/resources/css/db-browser.css">
		<title>DB Browser: ${cluster}</title>
	</head>
    <body>
		<div class="pageHeader">
			<span>DB Browser: ${cluster}</span>
		</div>
		<div style="position:absolute; left:50%; margin-left:-250px; width:600px; top:50%; margin-top:-75px; height:300px">
			<form id="loginForm" method="POST" action="${pageContext.request.contextPath}/j_spring_security_check">
				<table>
					<tr>
						<td colspan="2" class="largeText" style="text-align:center;"><b>Login to DB Browser</b></td>
						<td/>
					</tr>
					<tr>
						<td><b>User Name:</b></td>
						<td><input type='text' name='j_username' /></td>
					</tr>
					<tr>
						<td><b>Password:</b></td>
						<td><input type='password' name='j_password' /></td>
					</tr>
					<tr>
						<td/>
						<td>
							<input type="submit" class="buttonText" value="Login" />
						</td>
					</tr>
					<tr>
						<td/>
						<td>
							<c:if test="${param.error == 1}">
								<span class="errorText"><b>Access Denied: </b>${SPRING_SECURITY_LAST_EXCEPTION.message}</span>
							</c:if>
						</td>
					</tr>
				</table>
			</form>
		</div>
	</body>
</html>
