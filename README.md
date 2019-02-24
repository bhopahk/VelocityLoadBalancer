# VelocityLoadBalancer
A simple plugin to balance player counts on a Velocity proxy.

## Installation
  * Download the latest release from the [releases tab](https://github.com/bhopahk/VelocityLoadBalancer/releases).
  * Put the jar in your Velocity plugins folder
  * Start the server once
  * Add the desired lobby servers to the `config.toml` file located in `plugins/loadbalancer/`.
  * Restart Velocity

## Building LoadBalancer
  * Pull the repository in whichever way you are most comfortable
  * Run the `build` gradle task using a local gradle distribution or the included gradle wrapper.
