import java.time.Clock

import com.google.inject.AbstractModule

class Module extends AbstractModule {
  def configure() = {
    bind(classOf[Clock]).toInstance(Clock.systemUTC())
  }
}
