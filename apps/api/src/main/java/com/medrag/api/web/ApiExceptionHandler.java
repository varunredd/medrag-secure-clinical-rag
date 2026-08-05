package com.medrag.api.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class) ResponseEntity<Problem> notFound(NotFoundException e,HttpServletRequest r){return response(HttpStatus.NOT_FOUND,e.getMessage(),"Resource not found",r);}
    @ExceptionHandler(BadRequestException.class) ResponseEntity<Problem> bad(BadRequestException e,HttpServletRequest r){return response(HttpStatus.BAD_REQUEST,e.getMessage(),"Invalid request",r);}
    @ExceptionHandler(UnprocessableFileException.class) ResponseEntity<Problem> file(UnprocessableFileException e,HttpServletRequest r){return response(HttpStatus.UNPROCESSABLE_ENTITY,e.code(),e.getMessage(),r);}
    @ExceptionHandler(DependencyUnavailableException.class) ResponseEntity<Problem> unavailable(DependencyUnavailableException e,HttpServletRequest r){return response(HttpStatus.SERVICE_UNAVAILABLE,e.code(),e.getMessage(),r);}
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Problem> validation(MethodArgumentNotValidException e,HttpServletRequest r){return response(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Request validation failed",r);}
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<Problem> denied(AccessDeniedException e,HttpServletRequest r){return response(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Access denied",r);}
    @ExceptionHandler(WebClientResponseException.class) ResponseEntity<Problem> ai(WebClientResponseException e,HttpServletRequest r){return response(HttpStatus.BAD_GATEWAY,"AI_SERVICE_ERROR","AI service could not complete the request",r);}
    @ExceptionHandler(Exception.class) ResponseEntity<Problem> fallback(Exception e,HttpServletRequest r){return response(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR","Unexpected server error",r);}
    private ResponseEntity<Problem> response(HttpStatus status,String code,String detail,HttpServletRequest request){return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(new Problem("about:blank",status.getReasonPhrase(),status.value(),detail,request.getRequestURI(),code,MDC.get("requestId"),Instant.now()));}
    public record Problem(String type,String title,int status,String detail,String instance,String code,String requestId,Instant timestamp){}
}
