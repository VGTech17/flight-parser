package org.example;

import java.util.ArrayList;
import java.util.List;

public class FlightRecord {
    public String sid;
    public String date;
    public String departureTime;
    public String arrivalTime;
    public String operator;
    public String aircraftType;
    public String aircraftModel;
    public String status;
    public String departureCoords;
    public String arrivalCoords;
    public String remarks;
    public String sourceCenter;
    public List<String> phones = new ArrayList<>();
    public String remarksRaw;

    public FlightRecord(String sid, String date, String departureTime, String arrivalTime, String operator, String aircraftType, String aircraftModel, String status, String departureCoords, String arrivalCoords, String remarks, String sourceCenter, List<String> phones, String remarksRaw) {
        this.sid = sid;
        this.date = date;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.operator = operator;
        this.aircraftType = aircraftType;
        this.aircraftModel = aircraftModel;
        this.status = status;
        this.departureCoords = departureCoords;
        this.arrivalCoords = arrivalCoords;
        this.remarks = remarks;
        this.sourceCenter = sourceCenter;
        this.phones = phones;
        this.remarksRaw = remarksRaw;
    }

    public FlightRecord() {
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(String arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getAircraftType() {
        return aircraftType;
    }

    public void setAircraftType(String aircraftType) {
        this.aircraftType = aircraftType;
    }

    public String getAircraftModel() {
        return aircraftModel;
    }

    public void setAircraftModel(String aircraftModel) {
        this.aircraftModel = aircraftModel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDepartureCoords() {
        return departureCoords;
    }

    public void setDepartureCoords(String departureCoords) {
        this.departureCoords = departureCoords;
    }

    public String getArrivalCoords() {
        return arrivalCoords;
    }

    public void setArrivalCoords(String arrivalCoords) {
        this.arrivalCoords = arrivalCoords;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getSourceCenter() {
        return sourceCenter;
    }

    public void setSourceCenter(String sourceCenter) {
        this.sourceCenter = sourceCenter;
    }

    public List<String> getPhones() {
        return phones;
    }

    public void setPhones(List<String> phones) {
        this.phones = phones;
    }

    public String getRemarksRaw() {
        return remarksRaw;
    }

    public void setRemarksRaw(String remarksRaw) {
        this.remarksRaw = remarksRaw;
    }

    @Override
    public String toString() {
        return "FlightRecord{" +
                "sid='" + sid + '\'' +
                ", date='" + date + '\'' +
                ", departureTime='" + departureTime + '\'' +
                ", arrivalTime='" + arrivalTime + '\'' +
                ", operatorPersons=" + operator +
                ", aircraftType='" + aircraftType + '\'' +
                ", aircraftModel='" + aircraftModel + '\'' +
                ", status='" + status + '\'' +
                ", departureCoords='" + departureCoords + '\'' +
                ", arrivalCoords='" + arrivalCoords + '\'' +
                ", remarks='" + remarks + '\'' +
                ", sourceCenter='" + sourceCenter + '\'' +
                ", phones=" + phones +
                ", remarksRaw='" + remarksRaw + '\'' +
                '}';
    }
}