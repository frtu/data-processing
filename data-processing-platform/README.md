# data-processing-platform

## Goal

Creating an easy-to-adopt transformation framework for Kotlin with goals around enabling quick custom transformations
between input and output data classes.

- Topic Focus: Kotlin transformation framework design for
  seamless data class conversions
- Primary Goals: Framework simplicity, rapid custom
  transformation creation, developer adoption ease

## Samples

### Using local web UI

* Add to gradle : ```implementation(libs.flink.runtime.web)```
* Run [SplitWordCountWithWebUI.kt](src%2Fmain%2Fkotlin%2Fcom%2Fgithub%2Ffrtu%2Fdataprocessing%2Fsamples%2Fstandalone%2FSplitWordCountWithWebUI.kt)
  with Program argument `local`
* Go to http://localhost:8081/#/job/running
