package com.yangyx.iptools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.synaptictools.iperf.IPerf
import com.synaptictools.iperf.IPerfConfig
import com.synaptictools.iperf.iPerfNative
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `inspect iperf classes`() {
    println("=== IPerfConfig Fields & Methods ===")
    IPerfConfig::class.java.declaredFields.forEach {
      println("Field: ${it.name} : ${it.type.name}")
    }
    IPerfConfig::class.java.declaredMethods.forEach {
      println("Method: ${it.name}(${it.parameterTypes.map { p -> p.simpleName }.joinToString()}) : ${it.returnType.simpleName}")
    }

    println("=== iPerfNative Methods ===")
    iPerfNative::class.java.declaredMethods.forEach {
      println("Native Method: ${it.name}(${it.parameterTypes.map { p -> p.simpleName }.joinToString()}) : ${it.returnType.simpleName}")
    }

    println("=== IPerf Methods ===")
    IPerf::class.java.declaredMethods.forEach {
      println("IPerf Method: ${it.name}(${it.parameterTypes.map { p -> p.simpleName }.joinToString()}) : ${it.returnType.simpleName}")
    }
  }
}

