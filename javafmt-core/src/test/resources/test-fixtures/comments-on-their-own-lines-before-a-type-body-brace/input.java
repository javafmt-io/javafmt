interface Query {}

interface AlterDatabase
    extends
        Query
    //permits
    //    AlterDatabaseImpl
{
    String database();
}
