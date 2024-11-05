package factory;

//TODO this is a test use case for class Factory - to be ignored, and deleted later on
public class Main {

    public static void main(String[] args) {
        Factory<String, Book, String> factory = new Factory<>();

        factory.add("history", title -> new HistoryBook(title));
        factory.add("fantasy", title -> new FantasyBook(title));
        factory.add("fantasy", Main::staticCreateFantasyBook);
        Book book1 = factory.create("fantasy", "harry potter");


//        factory.add("fantasy", factory::instanceCreateFantasyBook);
//        Book book2 = factory.create("fantasy", "dune");
//
//        book1.read();
//        book2.read();

    }




    public static Book staticCreateFantasyBook(String title) {
        return new FantasyBook(title);
    }


    public abstract static class Book {
        private String title;

        public Book(String title) {
            this.title = title;
        }

        public void read() {
            System.out.println("reading " + title);
        }
    }


    public static class FantasyBook extends Book {
        public FantasyBook(String title) {
            super(title);
        }



        @Override
        public void read() {
            System.out.println("reading fantasy " + super.title);
        }
    }

    public static class HistoryBook extends Book {
        public HistoryBook(String title) {
            super(title);
        }

        @Override
        public void read() {
            System.out.println("reading history " + super.title);
        }
    }

}





