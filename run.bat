@echo off
call mvn clean compile exec:java -Dexec.mainClass="com.nncartrack.Simulation"
pause