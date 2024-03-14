let gridApi;

document.addEventListener('DOMContentLoaded', function () {

    

    let rowData = [];

    $.ajax({
        url: 'https://raw.githubusercontent.com/gabyah92/CMRIT2026Leaderboard/main/src/main/resources/participant_details.csv',
        success: function (csvData) {
            Papa.parse(csvData, {
                header: true,
                complete: function (results) {
                    rowData = results.data;
                    // remove the first row if it starts with RollNumber
                    if (rowData[0]['Handle'] === 'RollNumber') {
                        rowData.shift();
                    console.log(rowData); // This will log the parsed CSV data
                    }
                }
            });

            console.log(rowData); // This will log the populated rowData array
            console.log(rowData[0]['GeeksForGeeksURLExists']); // This will log the value of the first row's 'GeeksForGeeksURLExists' column

            const gridOptions = {
                columnDefs: [
                    {
                        headerName: "#",
                        field: "rowIndex",
                        valueGetter: "node.rowIndex + 1",
                        width: 60,
                        pinned: 'left',
                        filter: false,
                        sortable: false,
                        lockPosition: true
                    },
                    {
                        headerName: 'Handle',
                        field: 'Handle',
                        filter: 'agTextColumnFilter',
                        floatingFilter: true
                    },
                    {
                        headerName: 'GeeksForGeeks Handle',
                        field: 'GeeksForGeeksHandle',
                        cellStyle: function (params) {
                            return {backgroundColor: check_url('GeeksForGeeksHandle', params.rowIndex) ? 'rag-red' : '#4d2731'};
                        },
                        filter: 'agTextColumnFilter',
                        floatingFilter: true
                    },
                    {
                        headerName: 'Codeforces Handle',
                        field: 'CodeforcesHandle',
                        cellStyle: function (params) {
                            return {backgroundColor: check_url('CodeforcesHandle', params.rowIndex) ? 'rag-red' : '#4d2731'};
                        },
                        filter: 'agTextColumnFilter',
                        floatingFilter: true
                    },
                    {
                        headerName: 'LeetCode Handle',
                        field: 'LeetCodeHandle',
                        cellStyle: function (params) {
                            return {backgroundColor: check_url('LeetCodeHandle', params.rowIndex) ? 'rag-red' : '#4d2731'};
                        },
                        filter: 'agTextColumnFilter',
                        floatingFilter: true
                    },
                    {
                        headerName: 'CodeChef Handle',
                        field: 'CodeChefHandle',
                        cellStyle: function (params) {
                            return {backgroundColor: check_url('CodeChefHandle', params.rowIndex) ? 'rag-red' : '#4d2731'};
                        },
                        filter: 'agTextColumnFilter',
                        floatingFilter: true
                    },
                    {
                        headerName: 'HackerRank Handle',
                        field: 'HackerRankHandle',
                        cellStyle: function (params) {
                            return {backgroundColor: check_url('HackerRankHandle', params.rowIndex) ? 'rag-red' : '#4d2731'};
                        },
                        filter: 'agTextColumnFilter',
                        floatingFilter: true
                    },
                ],
                defaultColDef: {
                    sortable: false,
                    filter: false,
                },
                rowData: rowData, // Assign the populated rowData array here
            };

            const egridDiv = document.querySelector('#myGrid');
            gridApi = agGrid.createGrid(egridDiv, gridOptions);
        }
    });

    /**
     * Check if a URL exists for a given column and row index.
     *
     * @param {string} col - The column name.
     * @param {number} rowIndex - The index of the row.
     * @returns {boolean} - True if the URL exists, false otherwise.
     */
    function check_url(col, rowIndex) {
        let url_exists;
        console.log("rowIndex: " + rowIndex);
        console.log("col: " + col);
        console.log("rowData[rowIndex]: " + rowData[rowIndex])
        if (col === 'GeeksForGeeksHandle') {
            url_exists = rowData[rowIndex]['GeeksForGeeksURLExists'];
            return url_exists === 'True';
        }
        if (col === 'CodeforcesHandle') {
            url_exists = rowData[rowIndex]['CodeforcesURLExists'];
            return url_exists === 'True';
        }
        if (col === 'LeetCodeHandle') {
            url_exists = rowData[rowIndex]['LeetCodeURLExists'];
            return url_exists === 'True';
        }
        if (col === 'CodeChefHandle') {
            url_exists = rowData[rowIndex]['CodeChefURLExists'];
            console.log("url_exists for CodeChef: " + url_exists);
            return url_exists === 'True';
        }
        if (col === 'HackerRankHandle') {
            url_exists = rowData[rowIndex]['HackerRankURLExists'];
            console.log("url_exists for HackerRank: " + url_exists);
            return url_exists === 'True';
        }
    }

});
