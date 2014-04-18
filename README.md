HttpClient.Scala
================

A Java-compatible HTTP client with VersionOne-specific methods, implemented in Scala.


    import com.versionone.httpclient.*
    import org.apache.oltu.oauth2.*
    
    val creds = new BasicOAuthToken(...)
    val settings = new OAuth2Settings(creds, ...)
    val log = LoggerFactory.getLogger("main")
    val v1 = new V1HttpClient(settings, log, "MyTest/1.0")
    val results =  v1.DoQuery("""
      from: Member
      select:
        - Name
        - Avatar.ContentType
        - Avatar.Content
        - OwnedWorkitems:PrimaryWorkitem.Estimate.@Sum
        - from: OwnedWorkitems:PrimaryWorkitem
          select:
            - Name
            - Number
            - Estimate
            - Description
            - ToDo
        """
    for {
      JMap(member) <- results
      JStr(mname) = member("name")
      JStr(itemsum) = member("OwnedWorkitems:PrimaryWorkitem.Estimate.@Sum")
    } {
      println((mname, itemsum))
    }
