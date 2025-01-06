/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "SourceFormatter.h"

#include <sstream>

namespace filament::matdbg {

namespace {

class ASStreamIterator : public astyle::ASSourceIterator {
public:// function declarations
    explicit ASStreamIterator(std::istringstream* in)
        : inStream(in),
          peekStart(0),
          prevLineDeleted(false),
          checkForEmptyLine(false) {
        buffer.reserve(200);
        // get length of stream
        inStream->seekg(0, inStream->end);
        streamLength = inStream->tellg();
        inStream->seekg(0, inStream->beg);
    }

    ~ASStreamIterator() override = default;

    int getStreamLength() const override { return static_cast<int>(streamLength); }

    std::string nextLine(bool emptyLineWasDeleted) override {
        // verify that the current position is correct
        assert(peekStart == 0);

        // a deleted line may be replaced if break-blocks is requested
        // this sets up the compare to check for a replaced empty line
        if (prevLineDeleted) {
            prevLineDeleted = false;
            checkForEmptyLine = true;
        }
        if (!emptyLineWasDeleted) prevBuffer = buffer;
        else
            prevLineDeleted = true;

        // read the next record
        buffer.clear();
        char ch;
        inStream->get(ch);

        while (!inStream->eof() && ch != '\n' && ch != '\r') {
            buffer.append(1, ch);
            inStream->get(ch);
        }

        if (inStream->eof()) {
            return buffer;
        }

        lastOutputEOL.clear();
        lastOutputEOL.append(1, ch);

        int peekCh = inStream->peek();

        // find input end-of-line characters
        if (!inStream->eof()) {
            if (ch == '\r') {
                // CR+LF is windows otherwise Mac OS 9
                if (peekCh == '\n') {
                    lastOutputEOL.append(1, peekCh);
                    inStream->get();
                }
            } else {
                // LF is Linux, allow for improbable LF/CR
                if (peekCh == '\r') {
                    lastOutputEOL.append(1, peekCh);
                    inStream->get();
                }
            }
        } else {
            inStream->clear();
        }

        return buffer;
    }

    std::string peekNextLine() override {
        std::string nextLine_;
        char ch;

        if (!peekStart) peekStart = inStream->tellg();

        // read the next record
        inStream->get(ch);
        while (!inStream->eof() && ch != '\n' && ch != '\r') {
            nextLine_.append(1, ch);
            inStream->get(ch);
        }

        if (inStream->eof()) {
            return nextLine_;
        }

        int peekCh = inStream->peek();

        // remove end-of-line characters
        if (!inStream->eof()) {
            if ((peekCh == '\n' || peekCh == '\r') && peekCh != ch) inStream->get();
        }

        return nextLine_;
    }

    void peekReset() override {
        inStream->clear();
        inStream->seekg(peekStart);
        peekStart = 0;
    }

    void saveLastInputLine() { prevBuffer = buffer; }

    std::streamoff tellg() override { return inStream->tellg(); }

    // Inline function
    bool compareToInputBuffer(const std::string& nextLine_) const {
        return (nextLine_ == prevBuffer);
    }
    const std::string& getLastOutputEOL() const { return lastOutputEOL; }
    std::streamoff getPeekStart() const override { return peekStart; }
    bool hasMoreLines() const override { return !inStream->eof(); }

private:
    std::istringstream* inStream;// pointer to the input stream
    std::string buffer;         // current input line
    std::string prevBuffer;     // previous input line
    std::string lastOutputEOL;  // next output end of line char
    bool checkForEmptyLine;
    std::streamoff streamLength;// length of the input file stream
    std::streamoff peekStart;   // starting position for peekNextLine
    bool prevLineDeleted;       // the previous input line was deleted
};

}// namespace

SourceFormatter::SourceFormatter() {
    mFormatter.setCStyle();
    mFormatter.setSpaceIndentation(4);
    mFormatter.setFormattingStyle(astyle::STYLE_GOOGLE);
    mFormatter.setMaxCodeLength(100);
}

std::string SourceFormatter::format(char const* source) {
    std::istringstream inStream(source);
    std::stringstream outStream;
    ASStreamIterator streamIterator(&inStream);
    mFormatter.init(&streamIterator);

    while (mFormatter.hasMoreLines()) {
        outStream << mFormatter.nextLine();

        if (mFormatter.hasMoreLines()) {
            outStream << "\n";
        } else {
            // this can happen if the file if missing a closing brace and break-blocks is requested
            if (mFormatter.getIsLineReady()) {
                outStream << "\n";
                outStream << mFormatter.nextLine();
            }
        }
    }
    return outStream.str();
}

} // filament::matdbg
