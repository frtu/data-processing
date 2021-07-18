echo "-> Run bash individually - Type 'timebased-counterbuild' to build the project"
timebased-counterbuild() {
  echo "mvn clean package"
  mvn clean package
}
echo "-> Run bash individually - Type 'timebased-counterrun' to run the executable"
timebased-counterrun() {
  echo "Running executable > java -jar target/timebased-counter-1.0.0-SNAPSHOT.jar"
  java -jar target/timebased-counter-1.0.0-SNAPSHOT.jar
}

timebased-counterbuild
timebased-counterrun