
package androidx2j.parser.view
  
import androidx2j.parser.AttrParser

object Chronometer : IView {
    override val myParser = TextView.myParser + AttrParser.androidBuilder()
        .add("countDown") {
            // 
            todo()
        }
        .add("format") {
            // 
            todo()
        }
        .build()

    
}
  