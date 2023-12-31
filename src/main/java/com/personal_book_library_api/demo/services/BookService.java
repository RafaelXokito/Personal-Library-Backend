package com.personal_book_library_api.demo.services;

import java.util.List;

import com.nimbusds.jose.util.Pair;
import com.personal_book_library_api.demo.daos.ReaderBookRepository;
import com.personal_book_library_api.demo.daos.ReaderRepository;
import com.personal_book_library_api.demo.daos.WriterRepository;
import com.personal_book_library_api.demo.dtos.PageDTO;
import com.personal_book_library_api.demo.entities.*;
import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreFilter;
import org.springframework.stereotype.Service;

import com.personal_book_library_api.demo.daos.BookRepository;
import org.springframework.web.bind.annotation.PostMapping;

@Service
public class BookService {
    
    private BookRepository bookRepository;
    private WriterRepository writerRepository;
    private ReaderRepository readerRepository;
    private ReaderBookRepository readerBookRepository;
    public BookService() {
    }

    @Autowired
    public BookService(BookRepository bookRepository, WriterRepository writerRepository, ReaderRepository readerRepository, ReaderBookRepository readerBookRepository) {
        this.bookRepository = bookRepository;
        this.writerRepository = writerRepository;
        this.readerRepository = readerRepository;
        this.readerBookRepository = readerBookRepository;
    }

    public List<Book> findAll() {
        return bookRepository.findAll();
    }

    @Transactional
    public void save(Book book, String email) {
        Writer writer = writerRepository.findByEmail(email).orElseThrow();

        book.setWriter(writer);
        writer.addBook(book);
        bookRepository.save(book);
    }

    public Book findById(Long id) {
        return bookRepository.findById(id).orElseThrow();
    }

    @Transactional
    public ReaderBook addBook(String email, Long id) {
        Reader reader = readerRepository.findByEmail(email).orElseThrow();

        Book book = bookRepository.findById(id).orElseThrow();

        if (readerBookRepository.findByReaderAndBook(reader, book).isPresent()) {
            throw new RuntimeException("You already have this book");
        }

        ReaderBook readerBook = new ReaderBook(reader, book, 1);
        readerBookRepository.save(readerBook);

        book.addReaderBook(readerBook);
        reader.addReaderBook(readerBook);
        bookRepository.save(book);
        readerRepository.save(reader);

        return readerBook;
    }

    public boolean canRemove(String username, Long id) {
        return true;
    }

    @Transactional
    // Testing pre authorize annotation using a method and its parameters
    @PreAuthorize("@bookService.canRemove(#username, #id)")
    @PostAuthorize("returnObject.id == #id") // returnObject is the return object from the method (ReaderBook)
//    @PreFilter() // Pre and Post filter are only applied to Lists
//    @PostFilter()
    public ReaderBook removeBook(String username, Long id) {
        Reader reader = readerRepository.findByEmail(username).orElseThrow();

        Book book = bookRepository.findById(id).orElseThrow();

        ReaderBook readerBook = readerBookRepository.findByReaderAndBook(reader, book).orElseThrow();

        book.removeReaderBook(readerBook);
        reader.removeReaderBook(readerBook);

        readerBookRepository.delete(readerBook);

        if (reader.getCurrentBook() != null && reader.getCurrentBook().getId().equals(id)) {
            reader.getCurrentBook().removeCurrentReader(reader);
            reader.setCurrentBook(null);
        }

        reader.removeBook(book);
        book.removeReader(reader);
        readerRepository.save(reader);
        bookRepository.save(book);

        return readerBook;
    }

    @Transactional
    public PageDTO readBook(String username, Long bookId) {
        Reader reader = readerRepository.findByEmail(username).orElseThrow();

        Book book = bookRepository.findById(bookId).orElseThrow();

        if (readerBookRepository.findByReaderAndBook(reader, book).isEmpty()) {
            throw new RuntimeException("You don't have this book");
        }

        Book currentBook = reader.getCurrentBook();
        // If the reader has a current book different from the book that is asking to read
        // remove the reader from the current book
        if (currentBook != null && !bookId.equals(currentBook.getId())) {
            currentBook.removeCurrentReader(reader);
        }

        // If the reader doesn't have a current book or the current book is different from the book that is asking to read
        if (currentBook == null || !bookId.equals(currentBook.getId())) {
            reader.setCurrentBook(book);
            book.addCurrentReader(reader);
            readerRepository.save(reader);
            bookRepository.save(book);
        }

        ReaderBook readerBook = readerBookRepository.findByReaderAndBook(reader, book).orElseThrow();

        ResultFromPageDTO resultFromPageDTO = getPageDTO(readerBook, reader, book);

        return resultFromPageDTO.pageDTO;
    }

    @Data
    private static final class ResultFromPageDTO {
        private final PageDTO pageDTO;
        private final boolean sucess;
    }

    private static ResultFromPageDTO getPageDTO(ReaderBook readerBook, Reader reader, Book book) {
        int currentPage = readerBook.getCurrentPage();

        // Assuming a default font size fits 1000 characters per page
        int charsPerPageDefault = 1000;

        // Calculate the ratio of the reader's font size to the default font size
        double fontSizeRatio = (double) reader.getFontSize() / 12.0;

        // Adjust based on reader's preferred font size
        int charsPerPageAdjusted = (int) (charsPerPageDefault / fontSizeRatio);

        int start = (currentPage - 1) * charsPerPageAdjusted;

        // Ensure that 'start' don't exceed the book's content length.
        // Imagina a book with less than 1000 characters.
        if (start > book.getContent().length() & book.getContent().length() < charsPerPageDefault) {
            charsPerPageAdjusted = book.getContent().length();
            return new ResultFromPageDTO(new PageDTO(currentPage, charsPerPageAdjusted, book.getContent()), false);
        }

        int end = start + charsPerPageAdjusted;

        // Adjust the 'start' position to the next newline character
        if (start > 0) {
            int prevNewlinePosStart = book.getContent().lastIndexOf("\n", start);
            if (prevNewlinePosStart != -1) {
                start = prevNewlinePosStart + 1; // +1 to move past the newline character
            }
        }

        // Ensure we don't exceed the book's content length
        if (end > book.getContent().length()) {
            end = book.getContent().length();
        } else {
            // Find the previous newline character after the 'end' position
            int prevNewlinePos = book.getContent().lastIndexOf("\n", end);
            if (prevNewlinePos != -1 && prevNewlinePos > start) {
                end = prevNewlinePos; // Adjust the 'end' position to the previous newline character
            }
        }

        return new ResultFromPageDTO(new PageDTO(currentPage, charsPerPageAdjusted, book.getContent().substring(start, end)),  true);
    }

    private static int getMaxNumberOfPages(Book book, Reader reader) {
        int charsPerPageDefault = 1000;
        double fontSizeRatio = (double) reader.getFontSize() / 12.0;
        int charsPerPageAdjusted = (int) (charsPerPageDefault / fontSizeRatio);
        return (int) Math.ceil((double) book.getContent().length() / charsPerPageAdjusted);
    }


    @Transactional
    public PageDTO nextPage(String username) {
        Reader reader = readerRepository.findByEmail(username).orElseThrow();

        Book book = reader.getCurrentBook();
        if (book == null) {
            throw new RuntimeException("You don't have a current book");
        }

        ReaderBook readerBook = readerBookRepository.findByReaderAndBook(reader, book).orElseThrow();

        int currentPage = readerBook.getCurrentPage();
        if (currentPage <= getMaxNumberOfPages(book, reader)) {
            currentPage++;
        }
        readerBook.setCurrentPage(currentPage);

        ResultFromPageDTO resultFromPageDTO = getPageDTO(readerBook, reader, book);

        System.out.println(resultFromPageDTO.sucess);
        if (resultFromPageDTO.sucess) {
            readerBookRepository.save(readerBook);
        } else {
            readerBook.setCurrentPage(--currentPage);
            readerBookRepository.save(readerBook);
            resultFromPageDTO.pageDTO.setPage(currentPage);
        }

        return resultFromPageDTO.pageDTO;
    }

    @Transactional
    public PageDTO previousPage(String username) {
        Reader reader = readerRepository.findByEmail(username).orElseThrow();

        Book book = reader.getCurrentBook();
        if (book == null) {
            throw new RuntimeException("You don't have a current book");
        }

        ReaderBook readerBook = readerBookRepository.findByReaderAndBook(reader, book).orElseThrow();

        int currentPage = readerBook.getCurrentPage();
        if (currentPage > 1) {
            currentPage--;
        }
        readerBook.setCurrentPage(currentPage);

        ResultFromPageDTO resultFromPageDTO = getPageDTO(readerBook, reader, book);

        if (resultFromPageDTO.sucess) {
            readerBookRepository.save(readerBook);
        } else {
            resultFromPageDTO.pageDTO.setPage(++currentPage);
        }

        return resultFromPageDTO.pageDTO;
    }

    public List<Book> search(String title, String keyword, String writerName) {
        return bookRepository.search(title, keyword, writerName);
    }

    public List<Reader> getReaders(Long id) {
        Book book = bookRepository.findById(id).orElseThrow();
        List<ReaderBook> readerBooks = readerBookRepository.findByBook_Id(id);
        return readerBooks.stream().map(ReaderBook::getReader).toList();
    }

    public List<Reader> getCurrentReaders(Long id) {
        Book book = bookRepository.findById(id).orElseThrow();
        return book.getCurrentReaders();
    }

    public Writer getWriter(Long id) {
        Book book = bookRepository.findById(id).orElseThrow();
        return book.getWriter();
    }

    @Transactional
    public List<Book> getMyBooks(User user) {
        return user.getMyBooks();
    }
}
