class Fixture
{
  private   final int x   ;


        public Fixture(){this(0)   ;}
   public Fixture(int x){this.x=x;}
	<T>  Fixture(T t,int x)  {this(x);System.out.println(t)  ;}


 @Inject  public  Fixture(int x,int y){ this.x =  x+y ;}

}
